package dev.molang.iamzombieq.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the class-file metadata used by the binary-compatibility tests without defining or initializing the class.
 *
 * <p>This is deliberately an ABI-envelope reader rather than a bytecode verifier. Unknown attributes are skipped
 * only after their outer length has been checked, while every consumed constant-pool reference and every
 * ABI-relevant attribute is validated and must end exactly at its declared boundary.</p>
 */
public final class ClassFileAbiReader {
    static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private static final int MIN_SUPPORTED_MAJOR = 45;
    private static final int MAX_SUPPORTED_MAJOR = 69;
    private static final int MAX_ANNOTATION_DEPTH = 64;
    private static final int MAX_ANNOTATION_NODES = 100_000;
    private static final int ACC_PUBLIC = 0x0001;

    private ClassFileAbiReader() {
    }

    public static ClassInfo read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_CLASS_BYTES + 1);
        }
        if (bytes.length > MAX_CLASS_BYTES) {
            throw new InvalidClassFileException("class file exceeds " + MAX_CLASS_BYTES + " bytes");
        }
        return parse(bytes);
    }

    static ClassInfo parse(byte[] bytes) throws InvalidClassFileException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAX_CLASS_BYTES) {
            throw new InvalidClassFileException("class file exceeds " + MAX_CLASS_BYTES + " bytes");
        }
        return new Parser(bytes).parse();
    }

    public record ClassInfo(
            String internalName,
            int accessFlags,
            Optional<String> signature,
            List<String> declarationAnnotationDescriptors,
            List<MemberInfo> fields,
            List<MemberInfo> methods) {
        public ClassInfo {
            Objects.requireNonNull(internalName, "internalName");
            Objects.requireNonNull(signature, "signature");
            declarationAnnotationDescriptors = List.copyOf(declarationAnnotationDescriptors);
            fields = List.copyOf(fields);
            methods = List.copyOf(methods);
        }
    }

    public record MemberInfo(
            String name,
            String descriptor,
            int accessFlags,
            Optional<String> signature,
            List<String> declarationAnnotationDescriptors) {
        public MemberInfo {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(signature, "signature");
            declarationAnnotationDescriptors = List.copyOf(declarationAnnotationDescriptors);
        }

        public boolean isPublic() {
            return (accessFlags & ACC_PUBLIC) != 0;
        }
    }

    public static final class InvalidClassFileException extends IOException {
        InvalidClassFileException(String message) {
            super(message);
        }

        InvalidClassFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class Parser {
        private final Cursor cursor;
        private ConstantPool constantPool;

        private Parser(byte[] bytes) {
            cursor = new Cursor(bytes, 0, bytes.length);
        }

        private ClassInfo parse() throws InvalidClassFileException {
            long magic = cursor.u4("magic");
            if (magic != 0xcafebabeL) {
                throw cursor.failure("invalid class-file magic");
            }
            cursor.u2("minor_version");
            int major = cursor.u2("major_version");
            if (major < MIN_SUPPORTED_MAJOR || major > MAX_SUPPORTED_MAJOR) {
                throw cursor.failure("unsupported class-file major version " + major);
            }

            constantPool = readConstantPool();
            int access = cursor.u2("class access_flags");
            String internalName = constantPool.className(cursor.u2("this_class"), "this_class");
            int superClass = cursor.u2("super_class");
            if (superClass != 0) {
                constantPool.className(superClass, "super_class");
            }

            int interfaceCount = cursor.u2("interfaces_count");
            for (int index = 0; index < interfaceCount; index++) {
                constantPool.className(cursor.u2("interface"), "interface");
            }

            List<MemberInfo> fields = readMembers(false);
            List<MemberInfo> methods = readMembers(true);
            AttributeInfo attributes = readAttributes(cursor, "class " + internalName);
            cursor.requireEnd("class file");

            return new ClassInfo(
                    internalName,
                    access,
                    Optional.ofNullable(attributes.signature()),
                    attributes.annotations(),
                    fields,
                    methods);
        }

        private ConstantPool readConstantPool() throws InvalidClassFileException {
            int count = cursor.u2("constant_pool_count");
            if (count == 0) {
                throw cursor.failure("constant_pool_count must be at least one");
            }

            int[] tags = new int[count];
            Object[] values = new Object[count];
            for (int index = 1; index < count; index++) {
                int tag = cursor.u1("constant-pool tag");
                tags[index] = tag;
                switch (tag) {
                    case 1 -> {
                        int length = cursor.u2("CONSTANT_Utf8 length");
                        values[index] = cursor.modifiedUtf8(length, "CONSTANT_Utf8");
                    }
                    case 3, 4 -> cursor.skip(4, "CONSTANT_Integer/Float payload");
                    case 5, 6 -> {
                        cursor.skip(8, "CONSTANT_Long/Double payload");
                        if (index + 1 >= count) {
                            throw cursor.failure("CONSTANT_Long/Double cannot occupy the final pool slot");
                        }
                        index++;
                    }
                    case 7 -> values[index] = cursor.u2("CONSTANT_Class name_index");
                    case 8, 16, 19, 20 -> cursor.skip(2, "two-byte constant-pool payload");
                    case 9, 10, 11, 12, 17, 18 ->
                            cursor.skip(4, "four-byte constant-pool payload");
                    case 15 -> {
                        int referenceKind = cursor.u1("CONSTANT_MethodHandle reference_kind");
                        if (referenceKind < 1 || referenceKind > 9) {
                            throw cursor.failure("invalid CONSTANT_MethodHandle reference_kind " + referenceKind);
                        }
                        cursor.skip(2, "CONSTANT_MethodHandle reference_index");
                    }
                    default -> throw cursor.failure("unknown or reserved constant-pool tag " + tag);
                }
            }
            return new ConstantPool(tags, values, cursor);
        }

        private List<MemberInfo> readMembers(boolean methods) throws InvalidClassFileException {
            String kind = methods ? "method" : "field";
            int count = cursor.u2(kind + "s_count");
            List<MemberInfo> members = new ArrayList<>(Math.min(count, 256));
            for (int index = 0; index < count; index++) {
                int access = cursor.u2(kind + " access_flags");
                String name = constantPool.utf8(cursor.u2(kind + " name_index"), kind + " name");
                String descriptor =
                        constantPool.utf8(cursor.u2(kind + " descriptor_index"), kind + " descriptor");
                if (methods) {
                    validateMethodDescriptor(descriptor, cursor);
                } else {
                    validateFieldDescriptor(descriptor, cursor);
                }
                AttributeInfo attributes = readAttributes(cursor, kind + " " + name + descriptor);
                members.add(new MemberInfo(
                        name,
                        descriptor,
                        access,
                        Optional.ofNullable(attributes.signature()),
                        attributes.annotations()));
            }
            return List.copyOf(members);
        }

        private AttributeInfo readAttributes(Cursor source, String owner) throws InvalidClassFileException {
            int count = source.u2(owner + " attributes_count");
            String signature = null;
            boolean visibleAnnotationsSeen = false;
            boolean invisibleAnnotationsSeen = false;
            List<String> visibleAnnotations = new ArrayList<>();
            List<String> invisibleAnnotations = new ArrayList<>();

            for (int index = 0; index < count; index++) {
                String name = constantPool.utf8(
                        source.u2(owner + " attribute_name_index"), owner + " attribute name");
                long length = source.u4(owner + " attribute_length");
                Cursor payload = source.slice(length, owner + " attribute " + name);
                switch (name) {
                    case "Signature" -> {
                        if (signature != null) {
                            throw payload.failure(owner + " has duplicate Signature attributes");
                        }
                        if (length != 2) {
                            throw payload.failure(owner + " Signature attribute must contain exactly two bytes");
                        }
                        signature = constantPool.utf8(
                                payload.u2(owner + " Signature signature_index"), owner + " Signature");
                    }
                    case "RuntimeVisibleAnnotations" -> {
                        if (visibleAnnotationsSeen) {
                            throw payload.failure(owner + " has duplicate RuntimeVisibleAnnotations attributes");
                        }
                        visibleAnnotationsSeen = true;
                        visibleAnnotations.addAll(readAnnotations(payload, owner));
                    }
                    case "RuntimeInvisibleAnnotations" -> {
                        if (invisibleAnnotationsSeen) {
                            throw payload.failure(owner + " has duplicate RuntimeInvisibleAnnotations attributes");
                        }
                        invisibleAnnotationsSeen = true;
                        invisibleAnnotations.addAll(readAnnotations(payload, owner));
                    }
                    default -> payload.skip(payload.remaining(), owner + " unknown attribute " + name);
                }
                payload.requireEnd(owner + " attribute " + name);
            }
            List<String> annotations =
                    new ArrayList<>(visibleAnnotations.size() + invisibleAnnotations.size());
            annotations.addAll(visibleAnnotations);
            annotations.addAll(invisibleAnnotations);
            return new AttributeInfo(signature, List.copyOf(annotations));
        }

        private List<String> readAnnotations(Cursor payload, String owner)
                throws InvalidClassFileException {
            AnnotationBudget budget = new AnnotationBudget();
            int count = payload.u2(owner + " annotation count");
            List<String> annotations = new ArrayList<>(Math.min(count, 64));
            for (int index = 0; index < count; index++) {
                annotations.add(readAnnotation(payload, 0, budget, owner));
            }
            return List.copyOf(annotations);
        }

        private String readAnnotation(
                Cursor payload,
                int depth,
                AnnotationBudget budget,
                String owner) throws InvalidClassFileException {
            if (depth > MAX_ANNOTATION_DEPTH) {
                throw payload.failure(owner + " annotation nesting exceeds " + MAX_ANNOTATION_DEPTH);
            }
            budget.consume(1, payload, owner);
            String descriptor = constantPool.utf8(
                    payload.u2(owner + " annotation type_index"), owner + " annotation descriptor");
            validateAnnotationDescriptor(descriptor, payload);
            int pairCount = payload.u2(owner + " annotation pair count");
            budget.consume(pairCount, payload, owner);
            for (int index = 0; index < pairCount; index++) {
                constantPool.utf8(
                        payload.u2(owner + " annotation element_name_index"), owner + " annotation element name");
                readElementValue(payload, depth, budget, owner);
            }
            return descriptor;
        }

        private void readElementValue(
                Cursor payload,
                int depth,
                AnnotationBudget budget,
                String owner) throws InvalidClassFileException {
            if (depth > MAX_ANNOTATION_DEPTH) {
                throw payload.failure(owner + " annotation nesting exceeds " + MAX_ANNOTATION_DEPTH);
            }
            budget.consume(1, payload, owner);
            int tag = payload.u1(owner + " annotation element tag");
            switch (tag) {
                case 'B', 'C', 'I', 'S', 'Z' ->
                        constantPool.requireTag(payload.u2(owner + " annotation const_value_index"), 3, owner);
                case 'D' ->
                        constantPool.requireTag(payload.u2(owner + " annotation const_value_index"), 6, owner);
                case 'F' ->
                        constantPool.requireTag(payload.u2(owner + " annotation const_value_index"), 4, owner);
                case 'J' ->
                        constantPool.requireTag(payload.u2(owner + " annotation const_value_index"), 5, owner);
                case 's' ->
                        constantPool.requireTag(payload.u2(owner + " annotation const_value_index"), 1, owner);
                case 'e' -> {
                    constantPool.requireTag(payload.u2(owner + " annotation enum type_name_index"), 1, owner);
                    constantPool.requireTag(payload.u2(owner + " annotation enum const_name_index"), 1, owner);
                }
                case 'c' ->
                        constantPool.requireTag(payload.u2(owner + " annotation class_info_index"), 1, owner);
                case '@' -> readAnnotation(payload, depth + 1, budget, owner);
                case '[' -> {
                    int valueCount = payload.u2(owner + " annotation array length");
                    budget.consume(valueCount, payload, owner);
                    for (int index = 0; index < valueCount; index++) {
                        readElementValue(payload, depth + 1, budget, owner);
                    }
                }
                default -> throw payload.failure(
                        owner + " annotation has unknown element_value tag " + tag);
            }
        }
    }

    private record AttributeInfo(String signature, List<String> annotations) {
    }

    private static final class ConstantPool {
        private final int[] tags;
        private final Object[] values;
        private final Cursor cursor;

        private ConstantPool(int[] tags, Object[] values, Cursor cursor) {
            this.tags = tags;
            this.values = values;
            this.cursor = cursor;
        }

        private String utf8(int index, String context) throws InvalidClassFileException {
            requireTag(index, 1, context);
            return (String) values[index];
        }

        private String className(int index, String context) throws InvalidClassFileException {
            requireTag(index, 7, context);
            String name = utf8((Integer) values[index], context + " name");
            validateInternalName(name, cursor);
            return name;
        }

        private void requireTag(int index, int expected, String context)
                throws InvalidClassFileException {
            if (index <= 0 || index >= tags.length) {
                throw cursor.failure(context + " constant-pool index " + index + " is out of range");
            }
            if (tags[index] != expected) {
                throw cursor.failure(
                        context + " constant-pool index " + index + " has tag " + tags[index]
                                + ", expected " + expected);
            }
        }
    }

    private static final class AnnotationBudget {
        private int nodes;

        private void consume(int count, Cursor cursor, String owner)
                throws InvalidClassFileException {
            if (count < 0 || nodes > MAX_ANNOTATION_NODES - count) {
                throw cursor.failure(owner + " annotation graph exceeds " + MAX_ANNOTATION_NODES + " nodes");
            }
            nodes += count;
        }
    }

    private static final class Cursor {
        private final byte[] bytes;
        private int position;
        private final int limit;

        private Cursor(byte[] bytes, int position, int limit) {
            this.bytes = bytes;
            this.position = position;
            this.limit = limit;
        }

        private int u1(String context) throws InvalidClassFileException {
            require(1, context);
            return bytes[position++] & 0xff;
        }

        private int u2(String context) throws InvalidClassFileException {
            require(2, context);
            int value = ((bytes[position] & 0xff) << 8) | (bytes[position + 1] & 0xff);
            position += 2;
            return value;
        }

        private long u4(String context) throws InvalidClassFileException {
            long high = u2(context);
            long low = u2(context);
            return (high << 16) | low;
        }

        private String modifiedUtf8(int length, String context) throws InvalidClassFileException {
            require(length, context);
            int start = position;
            int end = start + length;
            char[] decoded = new char[length];
            int decodedLength = 0;
            int index = start;
            while (index < end) {
                int first = bytes[index++] & 0xff;
                if (first == 0) {
                    throw failure(context + " contains a raw NUL byte");
                }
                if (first <= 0x7f) {
                    decoded[decodedLength++] = (char) first;
                    continue;
                }
                if ((first & 0xe0) == 0xc0) {
                    if (index >= end) {
                        throw failure(context + " has a truncated two-byte modified UTF-8 sequence");
                    }
                    int second = bytes[index++] & 0xff;
                    if ((second & 0xc0) != 0x80) {
                        throw failure(context + " has an invalid modified UTF-8 continuation byte");
                    }
                    int value = ((first & 0x1f) << 6) | (second & 0x3f);
                    if (value == 0) {
                        if (first != 0xc0 || second != 0x80) {
                            throw failure(context + " has a noncanonical NUL encoding");
                        }
                    } else if (value < 0x80) {
                        throw failure(context + " has an overlong two-byte modified UTF-8 sequence");
                    }
                    decoded[decodedLength++] = (char) value;
                    continue;
                }
                if ((first & 0xf0) == 0xe0) {
                    if (end - index < 2) {
                        throw failure(context + " has a truncated three-byte modified UTF-8 sequence");
                    }
                    int second = bytes[index++] & 0xff;
                    int third = bytes[index++] & 0xff;
                    if ((second & 0xc0) != 0x80 || (third & 0xc0) != 0x80) {
                        throw failure(context + " has an invalid modified UTF-8 continuation byte");
                    }
                    int value = ((first & 0x0f) << 12)
                            | ((second & 0x3f) << 6)
                            | (third & 0x3f);
                    if (value < 0x800) {
                        throw failure(context + " has an overlong three-byte modified UTF-8 sequence");
                    }
                    decoded[decodedLength++] = (char) value;
                    continue;
                }
                throw failure(context + " has a forbidden modified UTF-8 lead byte");
            }
            position = end;
            return new String(decoded, 0, decodedLength);
        }

        private Cursor slice(long length, String context) throws InvalidClassFileException {
            if (length > Integer.MAX_VALUE) {
                throw failure(context + " length exceeds the supported address space");
            }
            int intLength = (int) length;
            require(intLength, context);
            Cursor result = new Cursor(bytes, position, position + intLength);
            position += intLength;
            return result;
        }

        private void skip(int length, String context) throws InvalidClassFileException {
            require(length, context);
            position += length;
        }

        private int remaining() {
            return limit - position;
        }

        private void require(int length, String context) throws InvalidClassFileException {
            if (length < 0 || length > remaining()) {
                throw failure(context + " is truncated");
            }
        }

        private void requireEnd(String context) throws InvalidClassFileException {
            if (position != limit) {
                throw failure(context + " has " + remaining() + " trailing bytes");
            }
        }

        private InvalidClassFileException failure(String message) {
            return new InvalidClassFileException(message + " at byte " + position);
        }
    }

    private static void validateInternalName(String name, Cursor cursor)
            throws InvalidClassFileException {
        if (name.isEmpty()
                || name.indexOf('.') >= 0
                || name.indexOf('[') >= 0
                || name.indexOf(';') >= 0
                || name.indexOf('\0') >= 0) {
            throw cursor.failure("invalid internal class name " + name);
        }
    }

    private static void validateAnnotationDescriptor(String descriptor, Cursor cursor)
            throws InvalidClassFileException {
        validateFieldDescriptor(descriptor, cursor);
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
            throw cursor.failure("annotation type is not an object descriptor: " + descriptor);
        }
    }

    private static void validateFieldDescriptor(String descriptor, Cursor cursor)
            throws InvalidClassFileException {
        int end = parseFieldType(descriptor, 0, cursor);
        if (end != descriptor.length()) {
            throw cursor.failure("invalid field descriptor " + descriptor);
        }
    }

    private static void validateMethodDescriptor(String descriptor, Cursor cursor)
            throws InvalidClassFileException {
        if (descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            throw cursor.failure("invalid method descriptor " + descriptor);
        }
        int position = 1;
        while (position < descriptor.length() && descriptor.charAt(position) != ')') {
            position = parseFieldType(descriptor, position, cursor);
        }
        if (position >= descriptor.length() || descriptor.charAt(position) != ')') {
            throw cursor.failure("invalid method descriptor " + descriptor);
        }
        position++;
        if (position < descriptor.length() && descriptor.charAt(position) == 'V') {
            position++;
        } else {
            position = parseFieldType(descriptor, position, cursor);
        }
        if (position != descriptor.length()) {
            throw cursor.failure("invalid method descriptor " + descriptor);
        }
    }

    private static int parseFieldType(String descriptor, int start, Cursor cursor)
            throws InvalidClassFileException {
        if (start >= descriptor.length()) {
            throw cursor.failure("truncated descriptor " + descriptor);
        }
        int position = start;
        int dimensions = 0;
        while (position < descriptor.length() && descriptor.charAt(position) == '[') {
            dimensions++;
            if (dimensions > 255) {
                throw cursor.failure("array descriptor has more than 255 dimensions");
            }
            position++;
        }
        if (position >= descriptor.length()) {
            throw cursor.failure("truncated descriptor " + descriptor);
        }
        char type = descriptor.charAt(position);
        if ("BCDFIJSZ".indexOf(type) >= 0) {
            return position + 1;
        }
        if (type != 'L') {
            throw cursor.failure("invalid field type in descriptor " + descriptor);
        }
        int end = descriptor.indexOf(';', position + 1);
        if (end < 0) {
            throw cursor.failure("unterminated object type in descriptor " + descriptor);
        }
        String internalName = descriptor.substring(position + 1, end);
        validateInternalName(internalName, cursor);
        return end + 1;
    }
}

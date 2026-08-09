package dev.molang.iamzombieq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassFileAbiReaderTest {
    private static final String INITIALIZER_PROPERTY =
            "dev.molang.iamzombieq.util.ClassFileAbiReaderTest.initialized";
    private static final String FIXTURE_OWNER =
            "dev/molang/iamzombieq/util/ClassFileAbiReaderTest$RichFixture";
    private static final String VISIBLE_DESCRIPTOR =
            "Ldev/molang/iamzombieq/util/ClassFileAbiReaderTest$Visible;";
    private static final String INVISIBLE_DESCRIPTOR =
            "Ldev/molang/iamzombieq/util/ClassFileAbiReaderTest$Invisible;";

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void clearInitializerTrap() {
        System.clearProperty(INITIALIZER_PROPERTY);
    }

    @Test
    void readsAbiMetadataWithoutLoadingOrInitializingTheClass() throws IOException {
        System.clearProperty(INITIALIZER_PROPERTY);
        byte[] bytes = richFixtureBytes();
        Path classFile = temporaryDirectory.resolve("RichFixture.class");
        Files.write(classFile, bytes);

        ClassFileAbiReader.ClassInfo info = ClassFileAbiReader.read(classFile);

        assertEquals(FIXTURE_OWNER, info.internalName());
        assertEquals(0x0031, info.accessFlags(), "reader must expose the raw class-file u2 access mask");
        assertEquals(
                "<T:Ljava/lang/Number;>Ljava/lang/Object;",
                info.signature().orElseThrow());
        assertEquals(
                List.of(VISIBLE_DESCRIPTOR, INVISIBLE_DESCRIPTOR),
                info.declarationAnnotationDescriptors(),
                "runtime-visible descriptors must precede runtime-invisible descriptors");

        ClassFileAbiReader.MemberInfo values = member(info.fields(), "values");
        assertTrue(values.isPublic());
        assertEquals("Ljava/util/List;", values.descriptor());
        assertEquals("Ljava/util/List<TT;>;", values.signature().orElseThrow());
        assertEquals(
                List.of(VISIBLE_DESCRIPTOR, INVISIBLE_DESCRIPTOR),
                values.declarationAnnotationDescriptors());

        ClassFileAbiReader.MemberInfo privateLong = member(info.fields(), "longConstant");
        assertFalse(privateLong.isPublic(), "private members still need structural parsing");
        assertEquals("J", privateLong.descriptor());

        ClassFileAbiReader.MemberInfo transform = member(info.methods(), "transform");
        assertTrue(transform.isPublic());
        assertEquals("(Ljava/util/List;)Ljava/util/List;", transform.descriptor());
        assertTrue(transform.signature().orElseThrow().contains("Ljava/util/List<+TT;>;"));
        assertEquals(
                List.of(VISIBLE_DESCRIPTOR, INVISIBLE_DESCRIPTOR),
                transform.declarationAnnotationDescriptors());

        assertNull(System.getProperty(INITIALIZER_PROPERTY),
                "reading bytes must not run the fixture's class initializer");
    }

    @Test
    void rejectsEveryTruncatedPrefixAndTrailingData() throws IOException {
        byte[] valid = richFixtureBytes();
        for (int cut = 0; cut < valid.length; cut++) {
            int prefixLength = cut;
            assertThrows(
                    ClassFileAbiReader.InvalidClassFileException.class,
                    () -> ClassFileAbiReader.parse(Arrays.copyOf(valid, prefixLength)),
                    "prefix length " + prefixLength + " must fail closed");
        }

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(
                ClassFileAbiReader.InvalidClassFileException.class,
                () -> ClassFileAbiReader.parse(trailing),
                "bytes after the class envelope must not be ignored");
    }

    @Test
    void rejectsMalformedEnvelopeConstantPoolAndAnnotationPayloads() throws IOException {
        byte[] valid = richFixtureBytes();

        byte[] badMagic = valid.clone();
        badMagic[0] = 0;
        assertMalformed(badMagic);

        byte[] futureVersion = valid.clone();
        futureVersion[6] = 0;
        futureVersion[7] = 70;
        assertMalformed(futureVersion);

        byte[] reservedConstantPoolTag = valid.clone();
        reservedConstantPoolTag[10] = 2;
        assertMalformed(reservedConstantPoolTag);

        SyntheticClass synthetic = syntheticAnnotationClass(0);
        ClassFileAbiReader.ClassInfo parsed = ClassFileAbiReader.parse(synthetic.bytes());
        assertEquals(List.of("Lfixture/Marker;"), parsed.declarationAnnotationDescriptors());

        byte[] malformedModifiedUtf8 = synthetic.bytes().clone();
        malformedModifiedUtf8[synthetic.firstUtf8PayloadOffset()] = (byte) 0xf0;
        assertMalformed(malformedModifiedUtf8);

        byte[] rawNulModifiedUtf8 = synthetic.bytes().clone();
        rawNulModifiedUtf8[synthetic.firstUtf8PayloadOffset()] = 0;
        assertMalformed(rawNulModifiedUtf8);

        byte[] overlongModifiedUtf8 = synthetic.bytes().clone();
        overlongModifiedUtf8[synthetic.firstUtf8PayloadOffset()] = (byte) 0xc1;
        overlongModifiedUtf8[synthetic.firstUtf8PayloadOffset() + 1] = (byte) 0x81;
        assertMalformed(overlongModifiedUtf8);

        byte[] unknownElementTag = synthetic.bytes().clone();
        unknownElementTag[synthetic.elementTagOffset()] = '!';
        assertMalformed(unknownElementTag);

        byte[] wrongConstantKind = synthetic.bytes().clone();
        putUnsignedShort(wrongConstantKind, synthetic.constantValueIndexOffset(), 6);
        assertMalformed(wrongConstantKind);

        byte[] overflowingAttribute = synthetic.bytes().clone();
        putInt(overflowingAttribute, synthetic.attributeLengthOffset(), 0xffffffff);
        assertMalformed(overflowingAttribute);

        assertThrows(
                ClassFileAbiReader.InvalidClassFileException.class,
                () -> ClassFileAbiReader.parse(new byte[ClassFileAbiReader.MAX_CLASS_BYTES + 1]),
                "the fixed input cap must reject oversized class files before parsing");
    }

    @Test
    void rejectsAnnotationArrayNestingBeyondTheFixedDepthBound() throws IOException {
        SyntheticClass deeplyNested = syntheticAnnotationClass(65);

        assertThrows(
                ClassFileAbiReader.InvalidClassFileException.class,
                () -> ClassFileAbiReader.parse(deeplyNested.bytes()),
                "array-only annotation nesting must share the annotation depth bound");
    }

    private static void assertMalformed(byte[] bytes) {
        assertThrows(
                ClassFileAbiReader.InvalidClassFileException.class,
                () -> ClassFileAbiReader.parse(bytes));
    }

    private static ClassFileAbiReader.MemberInfo member(
            List<ClassFileAbiReader.MemberInfo> members,
            String name) {
        return members.stream()
                .filter(member -> member.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static byte[] richFixtureBytes() throws IOException {
        String resource = "/"
                + ClassFileAbiReaderTest.class.getName().replace('.', '/')
                + "$RichFixture.class";
        try (InputStream input = ClassFileAbiReaderTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("missing compiled fixture " + resource);
            }
            return input.readAllBytes();
        }
    }

    private static SyntheticClass syntheticAnnotationClass(int arrayDepth) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int firstUtf8PayloadOffset;
        int attributeLengthOffset;
        int elementTagOffset;
        int constantValueIndexOffset;
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0xcafebabe);
            output.writeShort(0);
            output.writeShort(65);
            output.writeShort(9);

            output.writeByte(1);
            output.writeShort("fixture/Minimal".length());
            firstUtf8PayloadOffset = bytes.size();
            output.writeBytes("fixture/Minimal");
            output.writeByte(7);
            output.writeShort(1);
            writeUtf8(output, "java/lang/Object");
            output.writeByte(7);
            output.writeShort(3);
            writeUtf8(output, "RuntimeVisibleAnnotations");
            writeUtf8(output, "Lfixture/Marker;");
            writeUtf8(output, "value");
            output.writeByte(3);
            output.writeInt(1);

            output.writeShort(0x0021);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(1);
            output.writeShort(5);
            attributeLengthOffset = bytes.size();
            output.writeInt(11 + 3 * arrayDepth);
            output.writeShort(1);
            output.writeShort(6);
            output.writeShort(1);
            output.writeShort(7);
            for (int depth = 0; depth < arrayDepth; depth++) {
                output.writeByte('[');
                output.writeShort(1);
            }
            elementTagOffset = bytes.size();
            output.writeByte('I');
            constantValueIndexOffset = bytes.size();
            output.writeShort(8);
        }
        return new SyntheticClass(
                bytes.toByteArray(),
                firstUtf8PayloadOffset,
                attributeLengthOffset,
                elementTagOffset,
                constantValueIndexOffset);
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        output.writeByte(1);
        output.writeShort(value.length());
        output.writeBytes(value);
    }

    private static void putUnsignedShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private record SyntheticClass(
            byte[] bytes,
            int firstUtf8PayloadOffset,
            int attributeLengthOffset,
            int elementTagOffset,
            int constantValueIndexOffset) {
    }

    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Visible {
        int number();

        String text();

        Class<?> type();

        RetentionPolicy policy();

        Nested nested();

        int[] array();
    }

    @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    private @interface Invisible {
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Nested {
        String value();
    }

    @Visible(
            number = 7,
            text = "class",
            type = String.class,
            policy = RetentionPolicy.CLASS,
            nested = @Nested("nested"),
            array = {1, 2, 3})
    @Invisible
    public static final class RichFixture<T extends Number> {
        private static final long longConstant = 0x1020304050607080L;
        private static final double doubleConstant = 3.25d;

        static {
            System.setProperty(INITIALIZER_PROPERTY, "initialized");
        }

        @Visible(
                number = 8,
                text = "field",
                type = List.class,
                policy = RetentionPolicy.RUNTIME,
                nested = @Nested("field"),
                array = {4, 5})
        @Invisible
        public List<T> values;

        @Visible(
                number = 9,
                text = "method",
                type = Number.class,
                policy = RetentionPolicy.SOURCE,
                nested = @Nested("method"),
                array = {6})
        @Invisible
        public <E extends Exception> List<T> transform(List<? extends T> input) throws E {
            return List.copyOf(input);
        }
    }
}

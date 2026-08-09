package dev.molang.iamzombieq.util;

//? if >=1.21.11 {
import java.io.IOException;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdkClassFileAbiOracle {
    private JdkClassFileAbiOracle() {
    }

    public static ClassFileAbiReader.ClassInfo read(Path path) throws IOException {
        ClassModel model = ClassFile.of().parse(path);
        List<ClassFileAbiReader.MemberInfo> fields = model.fields().stream()
                .map(field -> new ClassFileAbiReader.MemberInfo(
                        field.fieldName().stringValue(),
                        field.fieldType().stringValue(),
                        field.flags().flagsMask(),
                        signature(field),
                        annotations(field)))
                .toList();
        List<ClassFileAbiReader.MemberInfo> methods = model.methods().stream()
                .map(method -> new ClassFileAbiReader.MemberInfo(
                        method.methodName().stringValue(),
                        method.methodType().stringValue(),
                        method.flags().flagsMask(),
                        signature(method),
                        annotations(method)))
                .toList();
        return new ClassFileAbiReader.ClassInfo(
                model.thisClass().asInternalName(),
                model.flags().flagsMask(),
                signature(model),
                annotations(model),
                fields,
                methods);
    }

    private static Optional<String> signature(AttributedElement element) {
        return element.findAttribute(Attributes.signature())
                .map(attribute -> attribute.signature().stringValue());
    }

    private static List<String> annotations(AttributedElement element) {
        List<String> annotations = new ArrayList<>();
        element.findAttribute(Attributes.runtimeVisibleAnnotations())
                .ifPresent(attribute -> attribute.annotations().forEach(
                        annotation -> annotations.add(annotation.className().stringValue())));
        element.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .ifPresent(attribute -> attribute.annotations().forEach(
                        annotation -> annotations.add(annotation.className().stringValue())));
        return List.copyOf(annotations);
    }
}
//?}

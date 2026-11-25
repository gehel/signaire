package fr.ledcom.signaire.android;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Java 17 is not Android: {@code System.getLogger}, for one, compiles and isn't on the phone, and d8 doesn't say so.
 * The code of the app and of the modules it stands on may only use, of Java, what {@code android.jar} has.
 */
class AndroidApiTest {

    private static final List<String> JAVA = List.of("java.", "javax.");

    @Test
    void theAppUsesOnlyTheJavaThatAndroidHas() throws IOException {
        JavaClasses android;
        try (JarFile jar = new JarFile(androidJar().toFile())) {
            android = new ClassFileImporter().importJar(jar);
        }
        JavaClasses app = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("fr.ledcom.signaire.model", "fr.ledcom.signaire.bundle",
                        "fr.ledcom.signaire.extractor", "fr.ledcom.signaire.offline", "fr.ledcom.signaire.android");

        TreeSet<String> missing = new TreeSet<>();
        for (JavaClass type : app) {
            for (JavaAccess<?> access : type.getAccessesFromSelf()) {
                AccessTarget target = access.getTarget();
                String owner = target.getOwner().getName();
                if (JAVA.stream().anyMatch(owner::startsWith) && !exists(android, target)) {
                    missing.add(target.getFullName() + " (" + access.getSourceCodeLocation() + ")");
                }
            }
        }

        assertThat(missing).as("Java that Android 14 doesn't have").isEmpty();
    }

    /** The member in the class of Android, or in one it inherits from. */
    private static boolean exists(JavaClasses android, AccessTarget target) {
        String owner = target.getOwner().getName();
        if (!android.contain(owner)) {
            return false;
        }
        List<JavaClass> types = new ArrayList<>();
        types.add(android.get(owner));
        for (int i = 0; i < types.size(); i++) {
            JavaClass type = types.get(i);
            if (declares(type, target)) {
                return true;
            }
            type.getRawSuperclass().ifPresent(types::add);
            types.addAll(type.getRawInterfaces());
        }
        return false;
    }

    private static boolean declares(JavaClass type, AccessTarget target) {
        if (target instanceof AccessTarget.FieldAccessTarget) {
            return type.getFields().stream().map(JavaField::getName).anyMatch(target.getName()::equals);
        }
        List<String> parameters = ((AccessTarget.CodeUnitAccessTarget) target).getRawParameterTypes().stream()
                .map(JavaClass::getName).toList();
        return type.getCodeUnits().stream().filter(unit -> unit.getName().equals(target.getName()))
                .map(JavaCodeUnit::getRawParameterTypes)
                .anyMatch(types -> types.stream().map(JavaClass::getName).toList().equals(parameters));
    }

    /** The android.jar of the SDK, a dependency of the module: {@code jar:file:/…/android.jar!/android/…}. */
    private static Path androidJar() {
        String activity = Objects.requireNonNull(ClassLoader.getSystemResource("android/app/Activity.class"),
                "android.jar on the class path").toString();
        return Path.of(URI.create(activity.substring("jar:".length(), activity.indexOf("!/"))));
    }
}

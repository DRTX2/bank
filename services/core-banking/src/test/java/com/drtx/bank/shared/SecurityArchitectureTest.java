package com.drtx.bank.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.drtx.bank.shared.security.Permission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the authorization model at build time: every endpoint is either explicitly public or protected by
 * {@code @PreAuthorize}, and every permission it names exists in the {@link Permission} catalogue (a typo would
 * otherwise silently lock everyone out, or worse).
 */
class SecurityArchitectureTest {

    private static final Set<String> PUBLIC_CONTROLLERS = Set.of("AuthController", "JwksController");
    private static final Pattern AUTHORITY = Pattern.compile("'([a-z]+:[a-z]+(?::[a-z]+)?)'");

    private final List<JavaClass> controllers = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.drtx.bank").stream()
            .filter(c -> c.isAnnotatedWith(RestController.class)).toList();

    @Test
    void everyEndpointDeclaresItsPermission() {
        List<String> unprotected = new ArrayList<>();
        for (JavaClass controller : controllers) {
            if (PUBLIC_CONTROLLERS.contains(controller.getSimpleName())) {
                continue;
            }
            Class<?> type = controller.reflect();
            for (Method m : type.getDeclaredMethods()) {
                boolean endpoint = java.util.Arrays.stream(m.getAnnotations())
                        .anyMatch(a -> a.annotationType().isAnnotationPresent(RequestMapping.class));
                if (endpoint && !m.isAnnotationPresent(PreAuthorize.class) && !type.isAnnotationPresent(PreAuthorize.class)) {
                    unprotected.add(type.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(controllers).isNotEmpty();
        assertThat(unprotected).isEmpty();
    }

    @Test
    void everyReferencedPermissionExists() {
        List<String> unknown = new ArrayList<>();
        for (JavaClass controller : controllers) {
            Class<?> type = controller.reflect();
            List<PreAuthorize> rules = new ArrayList<>();
            if (type.isAnnotationPresent(PreAuthorize.class)) {
                rules.add(type.getAnnotation(PreAuthorize.class));
            }
            for (Method m : type.getDeclaredMethods()) {
                if (m.isAnnotationPresent(PreAuthorize.class)) {
                    rules.add(m.getAnnotation(PreAuthorize.class));
                }
            }
            for (PreAuthorize rule : rules) {
                Matcher matcher = AUTHORITY.matcher(rule.value());
                while (matcher.find()) {
                    if (Permission.fromCode(matcher.group(1)).isEmpty()) {
                        unknown.add(type.getSimpleName() + ": " + matcher.group(1));
                    }
                }
            }
        }
        assertThat(unknown).isEmpty();
    }
}

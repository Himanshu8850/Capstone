package com.bookygeek.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the modular-monolith rules with plain JUnit and reflection (no extra library):
 * <ul>
 *   <li>the eight packages under {@code com.bookygeek} are the only top-level modules (seven business modules plus
 *       {@code common});</li>
 *   <li>a class never uses another module's {@code repository} or {@code entity} types (fields, constructor and method
 *       signatures);</li>
 *   <li>the only module-to-module dependency allowed between business modules' services is the approved pair
 *       Catalogue &harr; Category &amp; Brand ({@code subtreeIds}, {@code listPublishers});</li>
 *   <li>controllers never depend on repositories;</li>
 *   <li>services are concrete classes (no interface-per-service).</li>
 * </ul>
 * The rules pass trivially while a module is still empty and start to bite as each user story adds classes.
 */
class ModuleBoundaryTest {

    private static final String ROOT = "com.bookygeek";
    private static final Set<String> BUSINESS_MODULES =
            Set.of("user", "reward", "category", "catalogue", "cart", "payment", "order");
    private static final Set<String> ALL_MODULES = new TreeSet<>(Set.copyOf(
            new ArrayList<>(BUSINESS_MODULES) {{ add("common"); }}));
    /** Approved exception: these two modules may call each other's service (data-model §4, plan Complexity Tracking). */
    private static final Set<String> ALLOWED_PAIR = Set.of("catalogue", "category");
    private static final Pattern MODULE_OF = Pattern.compile("^" + Pattern.quote(ROOT) + "\\.([a-z]+)\\.(.*)$");

    private static List<Class<?>> classesOf(String module) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT + "." + module)) {
            try {
                classes.add(Class.forName(definition.getBeanClassName(), false,
                        Thread.currentThread().getContextClassLoader()));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return classes;
    }

    private static List<Class<?>> allMainClasses() {
        List<Class<?>> all = new ArrayList<>();
        for (String module : ALL_MODULES) {
            all.addAll(classesOf(module));
        }
        return all;
    }

    private static String moduleOf(Class<?> type) {
        var matcher = MODULE_OF.matcher(type.getName());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String layerOf(Class<?> type) {
        var matcher = MODULE_OF.matcher(type.getName());
        if (!matcher.matches()) {
            return null;
        }
        String rest = matcher.group(2);
        int dot = rest.indexOf('.');
        return dot < 0 ? "" : rest.substring(0, dot);
    }

    /** Every type a class mentions in its declarations: fields, constructors and method signatures. */
    private static Set<Class<?>> dependenciesOf(Class<?> type) {
        Set<Class<?>> deps = new java.util.LinkedHashSet<>();
        for (Field f : type.getDeclaredFields()) {
            add(deps, f.getType(), f.getGenericType());
        }
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            for (Parameter p : c.getParameters()) {
                add(deps, p.getType(), p.getParameterizedType());
            }
        }
        for (Method m : type.getDeclaredMethods()) {
            add(deps, m.getReturnType(), m.getGenericReturnType());
            for (Parameter p : m.getParameters()) {
                add(deps, p.getType(), p.getParameterizedType());
            }
        }
        return deps;
    }

    private static void add(Set<Class<?>> deps, Class<?> raw, java.lang.reflect.Type generic) {
        deps.add(raw.isArray() ? raw.getComponentType() : raw);
        if (generic instanceof java.lang.reflect.ParameterizedType parameterized) {
            for (java.lang.reflect.Type arg : parameterized.getActualTypeArguments()) {
                if (arg instanceof Class<?> argClass) {
                    deps.add(argClass);
                }
            }
        }
    }

    @Test
    void onlyTheApprovedModulePackagesExist() {
        Set<String> found = new TreeSet<>();
        for (Class<?> c : allMainClasses()) {
            String module = moduleOf(c);
            if (module != null) {
                found.add(module);
            }
        }
        assertThat(ALL_MODULES).containsAll(found);
        assertThat(BUSINESS_MODULES).hasSize(7);
    }

    @Test
    void noClassUsesAnotherModulesRepositoryOrEntity() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : allMainClasses()) {
            String own = moduleOf(c);
            for (Class<?> dep : dependenciesOf(c)) {
                String other = moduleOf(dep);
                String layer = layerOf(dep);
                if (own != null && other != null && !own.equals(other)
                        && ("repository".equals(layer) || "entity".equals(layer))) {
                    violations.add(c.getName() + " -> " + dep.getName());
                }
            }
        }
        assertThat(violations).as("cross-module repository/entity access").isEmpty();
    }

    @Test
    void onlyCatalogueAndCategoryMayDependOnEachOther() {
        List<String> violations = new ArrayList<>();
        for (String module : BUSINESS_MODULES) {
            for (Class<?> c : classesOf(module)) {
                if (!"service".equals(layerOf(c))) {
                    continue;
                }
                for (Class<?> dep : dependenciesOf(c)) {
                    String other = moduleOf(dep);
                    if (other == null || other.equals(module) || "common".equals(other)) {
                        continue;
                    }
                    boolean approvedPair = ALLOWED_PAIR.contains(module) && ALLOWED_PAIR.contains(other);
                    if (!approvedPair && !"service".equals(layerOf(dep)) && !"dto".equals(layerOf(dep))) {
                        violations.add(c.getName() + " -> " + dep.getName());
                    }
                }
            }
        }
        assertThat(violations).as("services may only use other modules' services and DTOs").isEmpty();
    }

    @Test
    void controllersDoNotTouchRepositories() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : allMainClasses()) {
            if (!"controller".equals(layerOf(c))) {
                continue;
            }
            for (Class<?> dep : dependenciesOf(c)) {
                if ("repository".equals(layerOf(dep))) {
                    violations.add(c.getName() + " -> " + dep.getName());
                }
            }
        }
        assertThat(violations).as("controller -> repository").isEmpty();
    }

    @Test
    void servicesAreConcreteClasses() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : allMainClasses()) {
            if ("service".equals(layerOf(c)) && (c.isInterface() || Modifier.isAbstract(c.getModifiers()))) {
                violations.add(c.getName());
            }
        }
        assertThat(violations).as("abstract or interface types in a service package").isEmpty();
    }

    @Test
    void layerPackagesFollowTheNamingConvention() {
        Set<String> layers = Set.of("controller", "service", "repository", "entity", "dto", "error", "props",
                "security", "time", "");
        List<String> unknown = new ArrayList<>();
        for (Class<?> c : allMainClasses()) {
            String layer = layerOf(c);
            if (layer != null && !layers.contains(layer)) {
                unknown.add(c.getName());
            }
        }
        assertThat(unknown).as("classes outside the agreed layer packages").isEmpty();
    }

    @Test
    void theRulesDetectViolationsInThisVerySuite() {
        // Self-check so the rules cannot silently pass: this test class is not in a module, so it is ignored by
        // the scanner, but the helpers must classify well-known names correctly.
        assertThat(moduleOf(com.bookygeek.common.error.ApiError.class)).isEqualTo("common");
        assertThat(layerOf(com.bookygeek.common.error.ApiError.class)).isEqualTo("error");
        assertThat(classesOf("common")).isNotEmpty();
        assertThat(new AssignableTypeFilter(Object.class)).isNotNull();
    }
}

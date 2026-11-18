package com.artivisi.accountingfinance.security;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EncryptedStringConverter} encrypts with a random IV, so the same plaintext never produces the
 * same ciphertext. A repository method that filters on an encrypted field (derived {@code findByNpwp}, or a
 * {@code @Query} comparing {@code e.npwp}) encrypts its parameter afresh and never matches a stored row,
 * and only on instances that have a key, which the default test profiles do not. Compare decrypted values
 * in Java instead.
 */
@DisplayName("Repositories do not filter on encrypted fields")
class EncryptedFieldQueryTest {

    private static final String ENTITY_PACKAGE = "com.artivisi.accountingfinance.entity";
    private static final String REPOSITORY_PATTERN = "classpath*:com/artivisi/accountingfinance/repository/*.class";

    @Test
    @DisplayName("No derived query or @Query condition references an encrypted field of the repository's entity")
    void noRepositoryFiltersOnEncryptedFields() throws IOException {
        Map<Class<?>, Set<String>> encrypted = encryptedFieldsByEntity();
        assertThat(encrypted).as("entities with encrypted fields").isNotEmpty();

        List<String> violations = new ArrayList<>();
        int repositories = 0;
        for (Class<?> repository : repositoryInterfaces()) {
            Class<?> domain = ResolvableType.forClass(repository).as(Repository.class).getGeneric(0).resolve();
            Set<String> fields = encrypted.get(domain);
            repositories++;
            if (fields == null) {
                continue;
            }
            for (Method method : repository.getDeclaredMethods()) {
                for (String field : fields) {
                    if (derivedQueryUses(method.getName(), field) || annotatedQueryFilters(method, field)) {
                        violations.add(repository.getSimpleName() + "." + method.getName() + " -> " + field);
                    }
                }
            }
        }
        assertThat(repositories).as("repository interfaces scanned").isGreaterThan(10);
        assertThat(violations).as("repository methods filtering on encrypted fields").isEmpty();
    }

    private static boolean derivedQueryUses(String methodName, String field) {
        if (!methodName.matches("^(find|exists|count|delete|get|read|query|search|stream|remove)\\w*By\\w+")) {
            return false;
        }
        String criteria = methodName.substring(methodName.indexOf("By") + 2);
        return Pattern.compile("(^|And|Or)" + StringUtils.capitalize(field) + "(?![a-z])").matcher(criteria).find();
    }

    private static boolean annotatedQueryFilters(Method method, String field) {
        Query query = method.getAnnotation(Query.class);
        if (query == null) {
            return false;
        }
        String f = Pattern.quote(field);
        return Pattern.compile("(?i)\\.\\s*" + f + "\\s*(=|<>|!=|like\\b|in\\b)"
                + "|(=|<>|!=|like)\\s*\\w+\\s*\\.\\s*" + f + "\\b").matcher(query.value()).find();
    }

    private static Map<Class<?>, Set<String>> encryptedFieldsByEntity() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Map<Class<?>, Set<String>> result = new HashMap<>();
        for (BeanDefinition bean : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            Class<?> type = ClassUtils.resolveClassName(bean.getBeanClassName(), EncryptedFieldQueryTest.class.getClassLoader());
            ReflectionUtils.doWithFields(type, field -> {
                Convert convert = field.getAnnotation(Convert.class);
                if (convert != null && convert.converter() == EncryptedStringConverter.class) {
                    result.computeIfAbsent(type, t -> new HashSet<>()).add(field.getName());
                }
            });
        }
        return result;
    }

    private static List<Class<?>> repositoryInterfaces() throws IOException {
        List<Class<?>> result = new ArrayList<>();
        ClassLoader loader = EncryptedFieldQueryTest.class.getClassLoader();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(REPOSITORY_PATTERN)) {
            String file = resource.getFilename();
            if (file == null || file.contains("$")) {
                continue;
            }
            Class<?> type = ClassUtils.resolveClassName(
                    "com.artivisi.accountingfinance.repository." + file.substring(0, file.length() - ".class".length()), loader);
            if (type.isInterface() && Repository.class.isAssignableFrom(type)) {
                result.add(type);
            }
        }
        return result;
    }
}

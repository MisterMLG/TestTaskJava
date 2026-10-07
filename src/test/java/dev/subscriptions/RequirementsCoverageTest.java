package dev.subscriptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementsCoverageTest {
    static final Map<String, String> REQUIREMENTS = requirements();

    private static final List<Class<?>> TEST_CLASSES =
            List.of(SubscriptionFlowTest.class, SubscriptionLifecycleTest.class);

    @Test
    @DisplayName("Каждое бизнес-правило покрыто хотя бы одним тестом")
    void everyRequirementHasATest() {
        Map<String, Set<String>> testsByRequirement = testsByRequirement();

        List<String> uncovered = REQUIREMENTS.keySet().stream()
                .filter(id -> !testsByRequirement.containsKey(id))
                .toList();

        assertThat(uncovered).as("правила без тестов").isEmpty();
    }

    @Test
    @DisplayName("Теги тестов ссылаются только на правила из реестра")
    void everyTagIsAKnownRequirement() {
        Set<String> unknown = new TreeSet<>(testsByRequirement().keySet());
        unknown.removeAll(REQUIREMENTS.keySet());

        assertThat(unknown).as("теги без правила в реестре").isEmpty();
    }

    static Map<String, Set<String>> testsByRequirement() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Class<?> testClass : TEST_CLASSES) {
            for (Method method : testClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Test.class)) {
                    continue;
                }
                for (Tag tag : method.getAnnotationsByType(Tag.class)) {
                    result.computeIfAbsent(tag.value(), id -> new TreeSet<>())
                            .add(testClass.getSimpleName() + "." + method.getName());
                }
            }
        }
        return result;
    }

    static String matrix() {
        Map<String, Set<String>> tests = testsByRequirement();
        return REQUIREMENTS.entrySet().stream()
                .map(e -> "| " + e.getKey() + " | " + e.getValue() + " | "
                        + tests.getOrDefault(e.getKey(), Set.of()).stream()
                                .map(name -> "`" + name + "`")
                                .collect(Collectors.joining("<br>")) + " |")
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, String> requirements() {
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("BR-01", "Клиент в подписке обязателен");
        rules.put("BR-02", "В подписке есть хотя бы одна строка");
        rules.put("BR-03", "Число периодов в строке больше нуля");
        rules.put("BR-04", "Тариф строки доступен для подключения");
        rules.put("BR-05", "Подписку нельзя провести при нехватке денег на лицевом счете");
        rules.put("BR-06", "Отмененная подписка не создает движений");
        rules.put("BR-07", "Дата окончания = дата начала + максимальный срок среди строк");
        rules.put("BR-08", "Сумма строки = цена * периоды, итог = сумма строк; пересчет при каждом сохранении");
        rules.put("BR-09", "Цена строки подставляется из тарифа");
        rules.put("BR-10", "Проведенный платеж пополняет лицевой счет на свою сумму");
        rules.put("BR-11", "Регламентное задание переводит подписки в статусы по датам действия");
        rules.put("BR-12", "Отмена проведенной подписки возвращает деньги и снимает выручку");
        rules.put("BR-13", "Проведение подписки списывает итог со счета и признает выручку по тарифам и клиентам");
        rules.put("BR-14", "Дата документа и дата начала подставляются при создании");
        rules.put("BR-15", "Платеж требует клиента и положительную сумму");
        return rules;
    }

    @Test
    @DisplayName("Матрица трассировки выводится в отчет сборки")
    void printsMatrix() {
        System.out.println("TRACEABILITY-MATRIX-BEGIN\n" + matrix() + "\nTRACEABILITY-MATRIX-END");
    }
}

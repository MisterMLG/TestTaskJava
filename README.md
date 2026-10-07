# Сервис подписок на onno

Учет и управление подписками клиентов на тарифные планы на базе
[onno-framework](https://github.com/onno-erp/onno-framework) 3.4.0 (Java 21, Spring Boot 3.4).

В проекте нет ни одной вручную написанной таблицы, DTO, контроллера или формы. Описана только
бизнес-модель: справочники, документы, регистры и правила. Схему БД, REST API, веб-интерфейс и
историю миграций фреймворк строит по этой модели при старте.

## Запуск

Нужна JDK 21 (Gradle сам найдет ее как toolchain, если запускается на другой версии).

```bash
./gradlew bootRun
```

Интерфейс: http://localhost:8080

| Пользователь | Пароль | Роль |
|---|---|---|
| `admin` | `admin` | ADMIN, полный доступ |
| `manager` | `manager` | MANAGER, работа с клиентами, тарифами и документами |

Учетные записи заданы в `src/main/resources/application.yaml` и предназначены только для
разработки. База H2 хранится в `./data/`; чтобы начать с чистой базы, удалите этот каталог.

Другой порт: `./gradlew bootRun --args='--server.port=8090'`.

## Сценарий проверки

1. **Справочники -> Клиенты -> Создать.** Статус "Активен" и дата регистрации уже заполнены.
2. **Справочники -> Тарифы -> Создать.** Например "Базовый, месяц": цена 100, период 30 дней; и
   "Премиум, год": цена 1000, период 365 дней.
3. **Продажи -> Платежи -> Создать.** Клиент, сумма 2000, кнопка "Провести". В отчете
   **Отчеты -> Лицевые счета** остаток клиента станет 2000.
4. **Продажи -> Подписки -> Создать.** Клиент, две строки: "Базовый, месяц" на 3 периода и
   "Премиум, год" на 1 период. После "Записать" цены подставятся из тарифов, итог станет 1300,
   дата окончания - дата начала плюс 365 дней (максимум из 90 и 365).
5. **Провести.** Статус сменится на "Активна", остаток клиента станет 700, в отчете
   **Отчеты -> Выручка по тарифам** появятся 300 и 1000.
6. **Нехватка денег.** Новая подписка на "Премиум, год" для того же клиента не проводится:
   "Недостаточно средств на лицевом счете: доступно 700.00, требуется 1000.00".
7. **Отмена.** В проведенной подписке меню "..." -> "Отменить подписку", ввести причину. Статус
   станет "Отменена", проведение снимется, остаток вернется к 2000, выручка обнулится.
8. **Дашборд** (раздел "Обзор") показывает поступления, выручку, число подписок за период,
   диаграммы и последние документы.

## Модель

| Сущность | Концепт onno | Класс |
|---|---|---|
| Клиент | справочник | `domain/catalogs/Customer` |
| Тариф | справочник | `domain/catalogs/Tariff` |
| Платеж | документ | `domain/documents/Payment` |
| Подписка | документ с табличной частью | `domain/documents/Subscription`, `SubscriptionLine` |
| Статус клиента, статус подписки, способ оплаты | перечисления | `domain/enumerations/*` |
| Лицевой счет | регистр остатков, измерение: клиент, ресурс: сумма | `domain/registers/CustomerAccount` |
| Выручка по тарифам | регистр оборотов, измерения: тариф и клиент, ресурсы: сумма и периоды | `domain/registers/TariffRevenue` |

Статусы подписки: Черновик -> (проведение) -> Ожидает начала / Активна -> Истекла. Из любого
статуса, кроме "Отменена", подписку можно отменить.

### Как устроена логика

- **Проведение платежа** (`Payment.handlePosting`): приход в регистр "Лицевые счета".
- **Проведение подписки** (`Subscription.handlePosting`): расход из "Лицевых счетов" на итог и
  запись в "Выручку по тарифам" по каждой строке.
- **Нехватка денег**: регистр остатков объявлен без `allowNegative`, поэтому фреймворк сам
  отклоняет проведение, уводящее остаток в минус. Поверх этого `Subscription.requireFunds` дает
  понятное сообщение с доступной и требуемой суммой.
- **Отмененная подписка**: `handlePosting` выходит до формирования движений.
- **Отмена проведенной подписки** (`SubscriptionService.cancel`): отмена проведения, затем статус
  "Отменена" и причина. Движения снимаются, деньги возвращаются.
- **Автоподстановка**: дата документа и дата начала - инициализаторы полей и `onFilling`; цена
  строки, суммы, итог и дата окончания - `beforeWrite` при каждом сохранении.
- **Правила** (`Validated` + `BusinessRule`): клиент обязателен, хотя бы одна строка, число
  периодов больше нуля, тариф доступен для подключения.
- **Регламентное задание** (`jobs/SubscriptionStatusJob`, `@ScheduledJob` + `BackgroundTask`):
  раз в час приводит статусы к датам действия. Сама логика - `Subscription.expectedStatus(дата)`,
  поэтому тестируется с произвольной датой.
- **Интерфейс**: по одному `EntityView` на сущность (`ui/views`), меню в `ui/layouts/MainLayout`,
  дашборд в `ui/pages/DashboardPage`, действие отмены с модальным окном - `ActionSpec` в
  `SubscriptionView`.

## Тесты и трассировка требований

```bash
./gradlew clean check
```

24 теста на H2 в памяти. Каждый тест бизнес-правила помечен `@Tag("BR-xx")`.
`RequirementsCoverageTest` сверяет реестр правил с тегами: правило без теста или тег без правила
роняют сборку.

| ID | Правило | Тесты |
|---|---|---|
| BR-01 | Клиент в подписке обязателен | `SubscriptionFlowTest.customerIsRequired` |
| BR-02 | В подписке есть хотя бы одна строка | `SubscriptionFlowTest.atLeastOneLineIsRequired` |
| BR-03 | Число периодов в строке больше нуля | `SubscriptionFlowTest.periodsMustBePositive` |
| BR-04 | Тариф строки доступен для подключения | `SubscriptionFlowTest.tariffMustBeAvailable`<br>`SubscriptionLifecycleTest.postedSubscriptionSurvivesTariffWithdrawal` |
| BR-05 | Подписку нельзя провести при нехватке денег на лицевом счете | `SubscriptionFlowTest.insufficientFundsRejectsPosting`<br>`SubscriptionFlowTest.postingSubscriptionWritesOffAndRecognizesRevenue`<br>`SubscriptionLifecycleTest.repostCountsOwnPreviousExpense` |
| BR-06 | Отмененная подписка не создает движений | `SubscriptionFlowTest.cancelledSubscriptionMakesNoMovements`<br>`SubscriptionLifecycleTest.cancellingPostedSubscriptionRefunds` |
| BR-07 | Дата окончания = дата начала + максимальный срок среди строк | `SubscriptionFlowTest.resavingRecalculates`<br>`SubscriptionFlowTest.savingFillsPriceAmountsTotalAndEndDate` |
| BR-08 | Сумма строки = цена * периоды, итог = сумма строк; пересчет при каждом сохранении | `SubscriptionFlowTest.resavingRecalculates`<br>`SubscriptionFlowTest.savingFillsPriceAmountsTotalAndEndDate` |
| BR-09 | Цена строки подставляется из тарифа | `SubscriptionFlowTest.savingFillsPriceAmountsTotalAndEndDate`<br>`SubscriptionLifecycleTest.priceFollowsTariffOnlyBeforePosting` |
| BR-10 | Проведенный платеж пополняет лицевой счет на свою сумму | `SubscriptionFlowTest.paymentIncreasesBalance` |
| BR-11 | Регламентное задание переводит подписки в статусы по датам действия | `SubscriptionLifecycleTest.jobIsScheduledAndRuns`<br>`SubscriptionLifecycleTest.postingActivatesSubscriptionStartingToday`<br>`SubscriptionLifecycleTest.refreshMovesStatusesByDates`<br>`SubscriptionLifecycleTest.refreshSkipsDraftsAndCancelled` |
| BR-12 | Отмена проведенной подписки возвращает деньги и снимает выручку | `SubscriptionLifecycleTest.cancelIsRejectedTwiceOrWithoutReason`<br>`SubscriptionLifecycleTest.cancellingPostedSubscriptionRefunds` |
| BR-13 | Проведение подписки списывает итог со счета и признает выручку по тарифам и клиентам | `SubscriptionFlowTest.postingSubscriptionWritesOffAndRecognizesRevenue`<br>`SubscriptionLifecycleTest.repostCountsOwnPreviousExpense` |
| BR-14 | Дата документа и дата начала подставляются при создании | `SubscriptionLifecycleTest.datesAreFilledOnCreate` |
| BR-15 | Платеж требует клиента и положительную сумму | `SubscriptionLifecycleTest.paymentRequiresCustomerAndPositiveAmount` |

## Допущения

- **Срок строки** = число периодов * длительность периода тарифа. Срок подписки - максимум по строкам.
- **Отмена проведенной подписки возвращает деньги полностью**, без пересчета за использованный срок.
- **Цена в проведенной подписке зафиксирована.** Пока подписка не проведена, цена берется из
  тарифа при каждом сохранении; после проведения изменение тарифа ее не меняет.
- **Доступность тарифа проверяется до проведения.** Уже проведенная подписка остается валидной,
  если тариф позже сняли с продажи.
- **Блокировка клиента** - справочный признак, на оформление подписок не влияет: в условии такого
  правила нет.
- **Статус "Ожидает начала"** добавлен для проведенной подписки с датой начала в будущем.

## Известные ограничения

- Часть подписей формы фреймворк 3.4.0 формирует из логического имени сущности и не дает
  переопределить: заголовок "New Subscriptions", кнопка "Create" на форме создания, заголовок
  табличной части "Lines", название вкладки записи.
- Создание через REST без поля, у которого есть значение по умолчанию, оставляет это поле пустым
  (значение из инициализатора в запрос не попадает). Форма интерфейса передает такие поля сама.
  Для подписки статус и дата начала восстанавливаются при следующем сохранении.
- Диаграмма по регистру подписывает ссылочные категории идентификаторами, поэтому разрез по
  тарифам вынесен в отчет "Выручка по тарифам", а на дашборде показан разрез по клиентам.
- Значения перечислений в REST передаются идентификаторами (UUID), а не именами констант.

## Развертывание

```bash
docker build -t subscriptions .
docker run -d -p 8080:8080 -v subscriptions-data:/app/data \
  -e ONNO_AUTH_USERS_0_PASSWORD=<пароль admin> \
  -e ONNO_AUTH_USERS_1_PASSWORD=<пароль manager> \
  -e ONNO_AUTH_SESSION_REMEMBER_ME_KEY=<случайная строка> \
  subscriptions
```

Исполняемый jar без Docker: `./gradlew bootJar`, затем
`java -jar build/libs/subscriptions-0.1.0.jar`.

## Структура

```text
src/main/java/dev/subscriptions/
  domain/catalogs       справочники
  domain/enumerations   перечисления
  domain/documents      документы и табличная часть
  domain/registers      регистры остатков и оборотов
  repositories          типизированные репозитории
  service               отмена подписки, актуализация статусов
  jobs                  регламентное задание
  support               доступ к справочникам из хуков жизненного цикла
  ui/views              EntityView по сущностям
  ui/layouts            боковое меню
  ui/pages              дашборд
src/test/java/dev/subscriptions/
  SubscriptionFlowTest        расчеты, проведение, правила
  SubscriptionLifecycleTest   статусы, отмена, перепроведение, задание
  RequirementsCoverageTest    сверка правил и тестов
```

# Contributing

## Strings

- English is the fallback in `app/src/main/res/values/strings.xml`.
- Russian translations live in `app/src/main/res/values-ru/strings.xml`.
- Keep resource names identical across locales.
- Add new translations with the same resource names and add the locale to `resourceConfigurations` in `app/build.gradle.kts`.
- Keep new UI text in resources. Use Android format placeholders such as `%1$s` and `%2$d`.

The existing `tr()` calls use a compatibility map in `I18n.kt`. Add an entry there when migrating a phrase to a resource.

## Code style

- Keep comments short and specific.
- Explain why, not obvious code.
- Don't use em dashes in comments.

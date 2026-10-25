# Contributing

## Strings

- English is the fallback in `app/src/main/res/values/strings.xml`.
- Russian translations live in `app/src/main/res/values-ru/strings.xml`.
- Keep resource names identical across locales.
- New Compose UI should use `stringResource(R.string.key)`. For non-Compose code, use `context.getString(R.string.key)`.
- Use Android format placeholders such as `%1$s` and `%2$d`.
- Add new locales to `resourceConfigurations` in `app/build.gradle.kts`.

Existing UI code uses `tr()` through a compatibility map in `I18n.kt`. Keep it working while migrating older call sites to resource IDs, then remove unused map entries.

## Code style

- Keep comments short and specific.
- Add comments only when the reason isn't clear from the code.
- Avoid em dashes in comments.

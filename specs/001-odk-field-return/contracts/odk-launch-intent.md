# ODK Launch Intent Contract

**Action**: `org.fossify.phone` (release) / `org.fossify.phone.debug` (debug)

**Extras**:
| Key | Type | Required | Description |
|-----|------|----------|-------------|
| phoneNumber | String | Yes | Number to dial |
| value | String | No | Existing field value for concat |
| odk_field_id | String | Yes (new) | Target field ID for return |
| auto_return_connect | Boolean | No (default true) | Enable auto-return on connect |
| auto_return_disconnect | Boolean | No (default true) | Enable auto-save on disconnect |

**Category**: android.intent.category.DEFAULT

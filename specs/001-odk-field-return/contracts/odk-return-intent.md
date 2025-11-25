# ODK Return Intent Contract (setResult RESULT_OK)

**Extras** (populate original field):
| Key | Type | Description |
|-----|------|-------------|
| value | String | Full concatenated call data |
| total_duration | Double | Sum successful call durations (s) |
| successful_calls | Int | Count of connected calls |
| records | String | JSON List<OdkCallRecord> |
| field_id | String | Echo of launch field_id |

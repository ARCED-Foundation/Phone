<!-- Sync Impact Report
Version change: 0.0.0 → 1.0.0
Added sections: None
Removed sections: None
Modified principles: None (new constitution)
Templates requiring updates:
- ✅ .specify/templates/plan-template.md (constitution check section updated with compliance gates)
- ✅ .specify/templates/spec-template.md (no changes needed)
- ✅ .specify/templates/tasks-template.md (no changes needed)
- ✅ .claude/commands/speckit.constitution.md (no changes needed)
Follow-up TODOs: None
-->

# Fossify Phone Constitution

## Core Principles

### Privacy-First Design
User data privacy is non-negotiable; No collection without explicit consent; Minimal permissions requested; All functionality must work offline when possible

### Seamless External Integration
MUST support robust integration workflows with external systems; Standard intent protocols for third-party launches; Well-defined data return mechanisms; Graceful error handling and session management

### Metadata Accuracy
Call data MUST be precisely tracked and formatted; Connection-based timing not dial-based; Concurrent call independence; Validation of all exported data fields

### Build Variant Compatibility
Functionality MUST work across all build variants (core, foss, gplay); Debug and release intent filters simultaneously supported; No hardcoded variant limitations

### User Experience Clarity
ODK sessions MUST have clear visual indicators; Intuitive session exit mechanisms; Proper state persistence; User confirmation for critical actions

## Integration Standards
All external integrations MUST follow standard Android intent patterns; Data exchange formats MUST be documented; Integration points MUST be maintainable without breaking existing functionality; Session lifecycle MUST be clearly defined

## Testing Requirements
Integration workflows MUST have end-to-end tests; Call metadata accuracy MUST be validated; Session state persistence MUST be tested; Build variant compatibility MUST be verified; Error scenarios MUST have comprehensive test coverage

## Governance
Constitution takes precedence over all development practices; Amendments require documentation and testing impact assessment; All integration features MUST pass constitution compliance review; Code reviews MUST verify constitutional principle adherence

**Version**: 1.0.0 | **Ratified**: 2025-11-22 | **Last Amended**: 2025-11-22

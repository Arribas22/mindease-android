# Developer Guide - MindEase

## Project structure
- app/src/main/java/com/bravosix/mindease
  - UI screens
  - ViewModels
  - managers (billing, ads, model, insights)
- app/src/main/java/com/bravosix/mindease/db
  - entities
  - dao interfaces
  - Room database wiring

## Engineering principles
- Keep business logic out of composables
- Use explicit manager classes for external integrations
- Prefer local/offline functionality when possible
- Fail safe: graceful fallback for unavailable features

## Development checklist
- Verify app boot and navigation
- Verify Room migrations on schema changes
- Validate billing/ad code behind clear flags
- Run static analysis and fix warnings before commit

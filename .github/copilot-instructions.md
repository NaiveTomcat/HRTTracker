# Copilot Instructions

This file contains instructions for GitHub Copilot to help it generate code that is consistent with the existing codebase. The instructions are based on the recent edits made to the codebase, and they provide guidance on how to write code that fits well with the existing code.

## General Guidelines

- Follow the existing coding style and conventions used in the codebase.
- Use the same libraries and frameworks that are already being used in the codebase.
- Write code that is consistent with the existing architecture and design patterns used in the codebase.
- Avoid introducing new dependencies unless absolutely necessary.
- Write code that is easy to read and understand, and that follows best practices for readability and maintainability.

## Specific Instructions

- DO NOT modify the `MainActivity.kt` file when you are askes to write new UI components. Instead, focus on writing new composable functions and UI components, and write corrosponding @Preview functions for them in the same file. This will help keep the `MainActivity.kt` file clean and focused on its main purpose, which is to set up the main activity and navigation for the app.

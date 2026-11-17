package com.nexora.ai.society

data class SkillValidationResult(val valid: Boolean, val reason: String)

/** MAS-X skills are declarative instruction bundles, never executable scripts or plugins. */
object MasxSkillValidator {
    private val unsafePhrases = listOf(
        "bypass approval", "ignore security policy", "disable permissions",
        "reveal secrets", "exfiltrate", "execute arbitrary shell", "ignore all safety"
    )

    fun validate(skill: SkillManifest, registeredToolIds: Set<String>): SkillValidationResult {
        if (skill.id.length < 3 || skill.name.isBlank()) return SkillValidationResult(false,"Skill identity is incomplete.")
        if (skill.instructions.isBlank()) return SkillValidationResult(false,"Skill instructions cannot be empty.")
        if (skill.requiredTools.any { it !in registeredToolIds }) {
            return SkillValidationResult(false,"Skill requests an unregistered tool capability.")
        }
        val text = (skill.description + "\n" + skill.instructions).lowercase()
        if (unsafePhrases.any(text::contains)) {
            return SkillValidationResult(false,"Skill contains instructions that attempt to bypass security boundaries.")
        }
        if (Regex("(?i)(<script|javascript:|runtime\\.exec|processbuilder|/bin/sh)").containsMatchIn(text)) {
            return SkillValidationResult(false,"Executable content is not supported in declarative skills.")
        }
        return SkillValidationResult(true,"Skill manifest passed static validation. User approval is still required.")
    }
}

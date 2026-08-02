load("@aspect_rules_lint//lint:ktlint.bzl", "lint_ktlint_aspect")

ktlint = lint_ktlint_aspect(
    binary = Label("@com_github_pinterest_ktlint//file"),
    editorconfig = Label("//:.editorconfig"),
)

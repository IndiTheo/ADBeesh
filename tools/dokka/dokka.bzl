load("@rules_java//java:defs.bzl", "JavaInfo")

def _dokka_archive_impl(ctx):
    # Collect all transitive JARs from the dependency graph
    # This automatically includes everything from library rules and direct JAR files.
    transitive_jars = depset(
        direct = [f for d in ctx.attr.deps for f in d.files.to_list() if JavaInfo not in d and f.extension == "jar"],
        transitive = [d[JavaInfo].transitive_compile_time_jars for d in ctx.attr.deps if JavaInfo in d],
    )

    out_dir = ctx.actions.declare_directory(ctx.label.name + "_out")

    # Build Dokka CLI arguments using the Args object
    # This is more efficient than string concatenation.
    args = ctx.actions.args()
    args.add("-moduleName", ctx.attr.module_name)
    args.add("-outputDir", out_dir.path)
    args.add("-loggingLevel", ctx.attr.logging_level)

    # Construct the nested -sourceSet flag
    # Dokka 2.x CLI takes this as a single string argument.
    # We use a semicolon for path separation as it's the safest for Dokka's internal parser.
    ss_parts = [
        "-src", ctx.attr.src_dir,
        "-analysisPlatform", ctx.attr.platform,
        "-classpath", ";".join([j.path for j in transitive_jars.to_list()]),
    ]

    for link in ctx.attr.external_links:
        ss_parts.extend(["-externalDocumentationLinks", link])

    args.add("-sourceSet", " ".join(ss_parts))

    ctx.actions.run(
        inputs = depset(ctx.files.srcs, transitive = [transitive_jars]),
        outputs = [out_dir],
        executable = ctx.executable._cli,
        arguments = [args],
        mnemonic = "Dokka",
        progress_message = "Generating %s documentation for %s" % (ctx.attr.platform, ctx.label),
    )

    # 4. Package output into a zip file without the path hierarchy
    ctx.actions.run_shell(
        inputs = [out_dir],
        outputs = [ctx.outputs.zip],
        command = "cd %s && zip -q -r \"$OLDPWD/%s\" . > /dev/null" % (out_dir.path, ctx.outputs.zip.path),
        mnemonic = "ZipDokka",
    )

dokka_archive = rule(
    implementation = _dokka_archive_impl,
    doc = "A generic rule to generate Dokka documentation archives.",
    attrs = {
        "srcs": attr.label_list(
            allow_files = True,
            doc = "Source files to document."
        ),
        "deps": attr.label_list(
            allow_files = True,
            doc = "Dependencies (libraries or raw JARs) required for analysis."
        ),
        "src_dir": attr.string(
            default = "src/main/kotlin",
            doc = "The root directory of the sources within the build sandbox."
        ),
        "module_name": attr.string(
            mandatory = True,
            doc = "The module name displayed in the documentation."
        ),
        "platform": attr.string(
            default = "jvm",
            doc = "Dokka analysis platform: 'android', 'jvm', or 'js'."
        ),
        "logging_level": attr.string(
            default = "info",
            doc = "Dokka logging level."
        ),
        "external_links": attr.string_list(
            doc = "External documentation links in 'URL^package-list-URL' format."
        ),
        "_cli": attr.label(
            default = Label("//tools/dokka:dokka_cli"),
            executable = True,
            cfg = "exec",
            doc = "The Dokka CLI tool.",
        ),
    },
    outputs = {"zip": "%{module_name}-docs.zip"},
)

// `arend --mcp`: the MCP server agents reach the running daemons through, the skills it installs, and
// its registration with a project's agents. Apart from the language core, which needs none of it.
dependencies {
    implementation(project(":base"))
    // What the option table of base, whose query tools the server describes, is written against.
    compileOnly("commons-cli:commons-cli:1.4")
    // Its JSON-RPC is Jackson's; the CLI bundles Jackson, and IntelliJ ships its own.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.11.2")

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.11.2")
    testImplementation("junit:junit:4.13.1")
}

package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.language.c.CRuleBook
import de.markusressel.kodehighlighter.language.cpp.CppRuleBook
import de.markusressel.kodehighlighter.language.csharp.CSharpRuleBook
import de.markusressel.kodehighlighter.language.dart.DartRuleBook
import de.markusressel.kodehighlighter.language.diff.DiffRuleBook
import de.markusressel.kodehighlighter.language.go.GoRuleBook
import de.markusressel.kodehighlighter.language.java.JavaRuleBook
import de.markusressel.kodehighlighter.language.json.JsonRuleBook
import de.markusressel.kodehighlighter.language.kotlin.KotlinRuleBook
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.kodehighlighter.language.ocaml.OCamlRuleBook
import de.markusressel.kodehighlighter.language.properties.PropertiesRuleBook
import de.markusressel.kodehighlighter.language.python.PythonRuleBook
import de.markusressel.kodehighlighter.language.rust.RustRuleBook
import de.markusressel.kodehighlighter.language.swift.SwiftRuleBook
import de.markusressel.kodehighlighter.language.toml.TomlRuleBook
import de.markusressel.kodehighlighter.language.xml.XmlRuleBook
import de.markusressel.kodehighlighter.language.yaml.YamlRuleBook

/** The languages that code blocks are highlighted in, by the names used after the opening fence */
internal object FenceLanguages {

    /** `python title="x"` and `{ .python .annotate }` */
    private val languageName = "^\\{?\\s*\\.?([^\\s{}.]+)".toRegex()

    private fun language(vararg names: String, create: () -> LanguageRuleBook): List<Pair<String, Lazy<LanguageRuleBook>>> {
        val book = lazy(create)
        return names.map { it to book }
    }

    private val ruleBooks: Map<String, Lazy<LanguageRuleBook>> = listOf(
        language("c", "h") { CRuleBook() },
        language("cpp", "c++", "cc", "cxx", "hpp") { CppRuleBook() },
        language("csharp", "cs", "c#") { CSharpRuleBook() },
        language("dart") { DartRuleBook() },
        language("diff", "patch") { DiffRuleBook() },
        language("go", "golang") { GoRuleBook() },
        language("java") { JavaRuleBook() },
        language("json", "jsonc", "json5") { JsonRuleBook() },
        language("kotlin", "kt", "kts") { KotlinRuleBook() },
        language("markdown", "md") { MarkdownRuleBook() },
        language("ocaml", "ml") { OCamlRuleBook() },
        language("properties") { PropertiesRuleBook() },
        language("python", "py", "python3", "py3") { PythonRuleBook() },
        language("rust", "rs") { RustRuleBook() },
        language("swift") { SwiftRuleBook() },
        language("toml") { TomlRuleBook() },
        language("xml", "html", "htm", "svg") { XmlRuleBook() },
        language("yaml", "yml") { YamlRuleBook() },
    ).flatten().toMap()

    val frontMatter: LanguageRuleBook get() = ruleBooks.getValue("yaml").value

    /**
     * @param infoString what follows the opening fence
     * @return the rules of that language, null if there is no language or it is not known
     */
    fun ruleBookFor(infoString: String): LanguageRuleBook? {
        val name = languageName.find(infoString.trim())?.groupValues?.get(1) ?: return null
        return ruleBooks[name.lowercase()]?.value
    }
}

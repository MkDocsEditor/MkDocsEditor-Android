package de.markusressel.mkdocsrestclient.mkdocs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MkDocsConfigModel(
    @Json(name = "copyright")
    val copyright: String,
    @Json(name = "edit_uri")
    val editUri: String,
    @Json(name = "extra")
    val extra: Map<String, Any>,
    @Json(name = "extra_css")
    val extraCss: List<String>,
    @Json(name = "markdown_extensions")
    val markdownExtensions: List<Any>,

    @Json(name = "repo_name")
    val repoName: String,
    @Json(name = "repo_url")
    val repoUrl: String,

    @Json(name = "site_author")
    val siteAuthor: String,
    @Json(name = "site_description")
    val siteDescription: String,
    @Json(name = "site_dir")
    val siteDir: String,
    @Json(name = "site_name")
    val siteName: String,
    @Json(name = "site_url")
    val siteUrl: String,

    @Json(name = "theme")
    val theme: MkDocsConfigThemeModel,
)

@JsonClass(generateAdapter = true)
data class MkDocsConfigThemeModel(
    @Json(name = "name")
    val name: String,
    @Json(name = "palette")
    val palette: MkDocsConfigThemePalette,
    @Json(name = "custom_dir")
    val customDir: String,
)

@JsonClass(generateAdapter = true)
data class MkDocsConfigThemePalette(
    @Json(name = "primary")
    val Primary: String,
    @Json(name = "accent")
    val Accent: String,
)
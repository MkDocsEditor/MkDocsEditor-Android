package de.markusressel.mkdocseditor;

import com.airbnb.epoxy.EpoxyDataBindingLayouts;

@EpoxyDataBindingLayouts({
        R.layout.list_item_section,
        R.layout.list_item_document,
        R.layout.list_item_resource,
        R.layout.list_item_loading,
        R.layout.preference_item__text_info,
})
interface EpoxyConfig {
}
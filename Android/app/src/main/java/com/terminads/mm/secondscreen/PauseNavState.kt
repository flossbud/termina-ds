package com.terminads.mm.secondscreen

/** Which pause view the bottom screen shows. Local state, never game truth. */
enum class PauseView { ROOT, OPTIONS }

/**
 * Local navigation within the pause experience.
 *
 * Immutable: every transition returns a new value, so Compose sees a changed
 * object and the tests need no mutation bookkeeping. Only `paused` itself comes
 * from the game; everything here is the host's own state.
 *
 * Switching tab or category resets the row selection, which is the design's own
 * rule (handoff section 10, "Options interaction").
 */
data class PauseNavState(
    val view: PauseView = PauseView.ROOT,
    val tab: OptionsTab = OptionsTab.SETTINGS,
    val category: OptionsCategory = OptionsCategory.GRAPHICS,
    val selectedKey: OptionKey? = null,
) {
    fun openOptions() = copy(view = PauseView.OPTIONS)

    fun back() = copy(view = PauseView.ROOT, selectedKey = null)

    fun selectTab(tab: OptionsTab) =
        copy(tab = tab, category = OptionsCategory.GRAPHICS, selectedKey = null)

    fun selectCategory(category: OptionsCategory) =
        copy(category = category, selectedKey = null)

    fun selectRow(key: OptionKey) = copy(selectedKey = key)
}

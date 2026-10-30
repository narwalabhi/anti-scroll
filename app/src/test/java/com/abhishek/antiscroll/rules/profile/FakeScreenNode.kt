package com.abhishek.antiscroll.rules.profile

/** Hand-built screen trees for tests. IDs are short names; [node] adds Instagram's prefix. */
data class FakeScreenNode(
    override val viewId: String? = null,
    override val contentDescription: String? = null,
    override val isSelected: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    override val children: List<ScreenNode> = emptyList(),
) : ScreenNode

fun node(
    id: String? = null,
    desc: String? = null,
    selected: Boolean = false,
    vararg children: ScreenNode,
) = FakeScreenNode(
    viewId = id?.let { "com.instagram.android:id/$it" },
    contentDescription = desc,
    isSelected = selected,
    children = children.toList(),
)

/** A view Instagram keeps loaded behind the screen you're on. */
fun hidden(id: String, vararg children: ScreenNode) = FakeScreenNode(
    viewId = "com.instagram.android:id/$id",
    isVisibleToUser = false,
    children = children.toList(),
)

fun group(vararg children: ScreenNode) = FakeScreenNode(children = children.toList())

// @formatter:off
@file:Suppress("RedundantVisibilityModifier")

// Generated by me.him188.ani.utils.bbcode.BBCodeTestGenerator
package me.him188.ani.utils.bbcode

import kotlin.test.Test

public class GenBBSpecialsTest : BBCodeParserTestHelper() {
    @Test
    public fun parse1488478815() {
        BBCode.parse("[b] /[][/]Hello [/b]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", bold=true)
        }
    }

    @Test
    public fun parse127856769() {
        BBCode.parse("[i] /[][/]Hello [/i]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", italic=true)
        }
    }

    @Test
    public fun parse1671584257() {
        BBCode.parse("[u] /[][/]Hello [/u]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", underline=true)
        }
    }

    @Test
    public fun parse17359423() {
        BBCode.parse("[s] /[][/]Hello [/s]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", strikethrough=true)
        }
    }

    @Test
    public fun parse493385023() {
        BBCode.parse("[url] /[][/]Hello [/url]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", jumpUrl=" /[][/]Hello ")
        }
    }

    @Test
    public fun parse1015657025() {
        BBCode.parse("[img] /[][/]Hello [/img]")
        .run {
            assertImage(elements.at(0), imageUrl=" /[][/]Hello ")
        }
    }

    @Test
    public fun parse1294424479() {
        BBCode.parse("[quote] /[][/]Hello [/quote]")
        .run {
            assertQuote(elements.at(0)) {
                assertText(elements.at(0), value=" /[][/]Hello ")
            }
        }
    }

    @Test
    public fun parse1274924885() {
        BBCode.parse("[code] /[][/]Hello [/code]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", code=true)
        }
    }

    @Test
    public fun parse1772059667() {
        BBCode.parse("[mask] /[][/]Hello [/mask]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", mask=true)
        }
    }
}


// @formatter:on

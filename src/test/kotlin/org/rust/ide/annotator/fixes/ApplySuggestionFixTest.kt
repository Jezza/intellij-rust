/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import org.intellij.lang.annotations.Language
import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.fileTree
import org.rust.replaceCaretMarker

class ApplySuggestionFixTest : RsWithToolchainTestBase() {

    override fun runTest() {
        // fixme on 2020.1
        if (ApplicationInfo.getInstance().build <= BuildNumber.fromString("193")) {
            super.runTest()
        } else {
            System.err.println("ApplySuggestionFixTest is temporarily disabled for 201.* builds")
        }
    }

    override fun setUp() {
        super.setUp()
        project.rustSettings.modify { it.runExternalLinterOnTheFly = true }
    }

    fun `test rustc suggestion (machine applicable)`() = checkFixByText("""
        pub fn main() {
            let <weak_warning>x</weak_warning> = 0;
        }
    """, """
        pub fn main() {
            let _x = 0;
        }
    """)

    fun `test rustc suggestion (maybe incorrect)`() = checkFixByText("""
        struct Foo(i32);

        impl Foo {
            fn foo(self) -> i32 {
                <error>this</error>.0
            }
        }
    """, """
        struct Foo(i32);

        impl Foo {
            fn foo(self) -> i32 {
                self.0
            }
        }
    """)

    fun `test rustc suggestion (has placeholders)`() = checkFixByText("""
        struct S { x: i32, y: i32 }
        impl S {
            fn new() -> Self { <error>Self</error> }
        }
    """, """
        struct S { x: i32, y: i32 }
        impl S {
            fn new() -> Self { Self { /* fields */ } }
        }
    """)

    fun `test rustc suggestion (unspecified)`() = checkFixByText("""
        fn foo<'a>(x: &i32, y: &'a i32) -> &'a i32 {
            if x > y { <error>x</error> } else { y }
        }
    """, """
        fn foo<'a>(x: &'a i32, y: &'a i32) -> &'a i32 {
            if x > y { x } else { y }
        }
    """)

    @MinRustcVersion("1.29.0")
    fun `test clippy suggestion`() = checkFixByText("""
        pub fn main() {
            <weak_warning>if true { true } else { false }</weak_warning>;
        }
    """, """
        pub fn main() {
            true;
        }
    """, externalLinter = ExternalLinter.CLIPPY)

    private fun checkFixByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        externalLinter: ExternalLinter = ExternalLinter.DEFAULT
    ) {
        project.rustSettings.modify { it.externalLinter = externalLinter }
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                file("lib.rs", before)
            }
        }.create()
        val filePath = "src/lib.rs"
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath(filePath)!!)
        myFixture.checkHighlighting()
        val action = myFixture.getAllQuickFixes(filePath)
            .singleOrNull { it.text.startsWith("External Linter: ") }
            ?: return // BACKCOMPAT: Rust ???
        myFixture.launchAction(action)
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }
}

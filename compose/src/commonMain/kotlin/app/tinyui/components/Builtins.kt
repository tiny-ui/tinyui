package app.tinyui.components

import app.tinyui.components.generated.BuiltinSchemas
import app.tinyui.components.impl.BackHandlerComponent
import app.tinyui.components.impl.BottomSheetComponent
import app.tinyui.components.impl.BoxComponent
import app.tinyui.components.impl.ButtonComponent
import app.tinyui.components.impl.CheckboxComponent
import app.tinyui.components.impl.CodeFieldComponent
import app.tinyui.components.impl.ColumnComponent
import app.tinyui.components.impl.DividerComponent
import app.tinyui.components.impl.IconComponent
import app.tinyui.components.impl.IndexBarComponent
import app.tinyui.components.impl.LazyColumnComponent
import app.tinyui.components.impl.LoadingComponent
import app.tinyui.components.impl.MenuComponent
import app.tinyui.components.impl.OptionComponent
import app.tinyui.components.impl.PopoverComponent
import app.tinyui.components.impl.RadioButtonComponent
import app.tinyui.components.impl.RowComponent
import app.tinyui.components.impl.SectionHeaderComponent
import app.tinyui.components.impl.SelectComponent
import app.tinyui.components.impl.SpacerComponent
import app.tinyui.components.impl.TabsComponent
import app.tinyui.components.impl.TextComponent
import app.tinyui.components.impl.TextFieldComponent
import app.tinyui.components.impl.TopAppBarComponent

/**
 * The built-ins (docs/components.md §3); schemas come from schema/ via `tinyui schema`. This file is the whole public
 * side of them: each component is a file of its own in `impl`, named after it.
 */
fun ComponentRegistry.registerBuiltins(): ComponentRegistry = apply {
    register(BuiltinSchemas.Column) { scope -> ColumnComponent(scope) }
    register(BuiltinSchemas.Row) { scope -> RowComponent(scope) }
    register(BuiltinSchemas.Box) { scope -> BoxComponent(scope) }
    register(BuiltinSchemas.Text) { scope -> TextComponent(scope) }
    register(BuiltinSchemas.Button) { scope -> ButtonComponent(scope) }
    register(BuiltinSchemas.RadioButton) { scope -> RadioButtonComponent(scope) }
    register(BuiltinSchemas.Checkbox) { scope -> CheckboxComponent(scope) }
    register(BuiltinSchemas.TextField) { scope -> TextFieldComponent(scope) }
    register(BuiltinSchemas.CodeField) { scope -> CodeFieldComponent(scope) }
    register(BuiltinSchemas.Select) { scope -> SelectComponent(scope) }
    register(BuiltinSchemas.Option) { scope -> OptionComponent(scope) }
    register(BuiltinSchemas.LazyColumn) { scope -> LazyColumnComponent(scope) }
    register(BuiltinSchemas.Spacer) { scope -> SpacerComponent(scope) }
    register(BuiltinSchemas.Icon) { scope -> IconComponent(scope) }
    register(BuiltinSchemas.Loading) { scope -> LoadingComponent(scope) }
    register(BuiltinSchemas.BottomSheet) { scope -> BottomSheetComponent(scope) }
    register(BuiltinSchemas.Popover) { scope -> PopoverComponent(scope) }
    register(BuiltinSchemas.TopAppBar) { scope -> TopAppBarComponent(scope) }
    register(BuiltinSchemas.BackHandler) { scope -> BackHandlerComponent(scope) }
    register(BuiltinSchemas.Menu) { scope -> MenuComponent(scope) }
    register(BuiltinSchemas.Tabs) { scope -> TabsComponent(scope) }
    register(BuiltinSchemas.SectionHeader) { scope -> SectionHeaderComponent(scope) }
    register(BuiltinSchemas.IndexBar) { scope -> IndexBarComponent(scope) }
    register(BuiltinSchemas.Divider) { scope -> DividerComponent(scope) }
}

package app.tinyui.components

import app.tinyui.components.generated.BuiltinSchemas
import app.tinyui.components.internal.BackHandlerComponent
import app.tinyui.components.internal.BottomSheetComponent
import app.tinyui.components.internal.BoxComponent
import app.tinyui.components.internal.ButtonComponent
import app.tinyui.components.internal.CheckboxComponent
import app.tinyui.components.internal.CodeFieldComponent
import app.tinyui.components.internal.ColumnComponent
import app.tinyui.components.internal.DividerComponent
import app.tinyui.components.internal.IconComponent
import app.tinyui.components.internal.IndexBarComponent
import app.tinyui.components.internal.LazyColumnComponent
import app.tinyui.components.internal.LoadingComponent
import app.tinyui.components.internal.MenuComponent
import app.tinyui.components.internal.OptionComponent
import app.tinyui.components.internal.PopoverComponent
import app.tinyui.components.internal.RadioButtonComponent
import app.tinyui.components.internal.RowComponent
import app.tinyui.components.internal.SectionHeaderComponent
import app.tinyui.components.internal.SelectComponent
import app.tinyui.components.internal.SpacerComponent
import app.tinyui.components.internal.TabsComponent
import app.tinyui.components.internal.TextComponent
import app.tinyui.components.internal.TextFieldComponent
import app.tinyui.components.internal.TopAppBarComponent
import app.tinyui.schema.ComponentRegistry

/**
 * The built-ins (docs/components.md §3); schemas come from schema/ via `tinyui schema`. This file is the whole public
 * side of them: each component is a file of its own in `internal`, named after it.
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

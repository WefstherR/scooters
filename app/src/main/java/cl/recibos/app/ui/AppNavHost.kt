package cl.recibos.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.recibos.app.AppContainer
import cl.recibos.app.ui.editor.EditorScreen
import cl.recibos.app.ui.home.HomeScreen
import cl.recibos.app.ui.receipt.ReceiptScreen
import cl.recibos.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?id={id}"
    const val RECEIPT = "receipt/{id}?issued={issued}"

    fun editor(id: Long = 0) = "editor?id=$id"
    fun receipt(id: Long, justIssued: Boolean = false) = "receipt/$id?issued=$justIssued"
}

/**
 * Flujo principal: Inicio → Nuevo comprobante → Vista previa → Confirmar y emitir → Compartir.
 */
@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onNewReceipt = { nav.navigate(Routes.editor()) },
                onOpenReceipt = { receipt ->
                    nav.navigate(if (receipt.isDraft) Routes.editor(receipt.id) else Routes.receipt(receipt.id))
                },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
        ) { entry ->
            EditorScreen(
                container = container,
                receiptId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onPreview = { id -> nav.navigate(Routes.receipt(id)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.RECEIPT,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("issued") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ReceiptScreen(
                container = container,
                receiptId = id,
                justIssued = entry.arguments?.getBoolean("issued") ?: false,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.editor(id)) { popUpTo(Routes.HOME) } },
                onIssued = { nav.navigate(Routes.receipt(id, justIssued = true)) { popUpTo(Routes.HOME) } },
                onOpenDraft = { newId -> nav.navigate(Routes.editor(newId)) { popUpTo(Routes.HOME) } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}

package com.rf.inventario.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rf.inventario.InventarioApplication
import com.rf.inventario.data.AppUser
import com.rf.inventario.data.MovementType
import com.rf.inventario.data.OrderType
import com.rf.inventario.ui.counts.CountDetailScreen
import com.rf.inventario.ui.counts.CountsScreen
import com.rf.inventario.ui.history.HistoryScreen
import com.rf.inventario.ui.home.HomeScreen
import com.rf.inventario.ui.location.LocationScreen
import com.rf.inventario.ui.movement.MovementScreen
import com.rf.inventario.ui.orders.OrderDetailScreen
import com.rf.inventario.ui.orders.OrdersScreen
import com.rf.inventario.ui.pending.PendingScreen
import com.rf.inventario.ui.products.ProductDetailScreen
import com.rf.inventario.ui.products.ProductEditorScreen
import com.rf.inventario.ui.products.ProductsScreen
import com.rf.inventario.ui.settings.SettingsScreen

/** Rutas de la app. Un solo lugar para no escribir strings sueltos. */
object Routes {
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val HISTORY = "history"
    const val PENDING = "pending"
    const val SETTINGS = "settings"
    const val COUNTS = "counts"

    fun product(id: Long) = "product/$id"
    fun editor(id: Long = 0, sku: String = "") = "editor?id=$id&sku=${android.net.Uri.encode(sku)}"
    fun movement(type: MovementType, code: String = "") = "move/${type.name}?code=${android.net.Uri.encode(code)}"
    fun count(id: Long) = "count/$id"
    fun orders(type: OrderType) = "orders/${type.name}"
    fun order(id: Long) = "order/$id"
    fun location(code: String = "") = "location?code=${android.net.Uri.encode(code)}"
}

/** ViewModel construido desde el contenedor de la app, con argumentos de navegación. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (InventarioApplication, SavedStateHandle) -> VM,
): VM = viewModel(
    factory = viewModelFactory {
        initializer { create(this[APPLICATION_KEY] as InventarioApplication, createSavedStateHandle()) }
    },
)

@Composable
fun AppNavHost(user: AppUser, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(user = user, onNavigate = { nav.navigate(it) }, onLogout = onLogout)
        }
        composable(Routes.PRODUCTS) {
            ProductsScreen(onBack = back, onNavigate = { nav.navigate(it) })
        }
        composable(
            "product/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            ProductDetailScreen(user = user, onBack = back, onNavigate = { nav.navigate(it) })
        }
        composable(
            "editor?id={id}&sku={sku}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("sku") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProductEditorScreen(onBack = back, onSaved = { id ->
                nav.popBackStack()
                if (id != null) nav.navigate(Routes.product(id)) { launchSingleTop = true }
            })
        }
        composable(
            "move/{type}?code={code}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("code") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            MovementScreen(onBack = back, onCreateProduct = { sku -> nav.navigate(Routes.editor(sku = sku)) })
        }
        composable(Routes.COUNTS) {
            CountsScreen(onBack = back, onOpen = { nav.navigate(Routes.count(it)) })
        }
        composable("count/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            CountDetailScreen(user = user, onBack = back)
        }
        composable("orders/{type}", arguments = listOf(navArgument("type") { type = NavType.StringType })) {
            OrdersScreen(onBack = back, onOpen = { nav.navigate(Routes.order(it)) })
        }
        composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            OrderDetailScreen(onBack = back)
        }
        composable(
            "location?code={code}",
            arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" }),
        ) {
            LocationScreen(onBack = back, onProduct = { nav.navigate(Routes.product(it)) })
        }
        composable(Routes.HISTORY) { HistoryScreen(onBack = back) }
        composable(Routes.PENDING) { PendingScreen(onBack = back) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
    }
}

package com.nandini.hanova

import android.Manifest
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nandini.hanova.data.Homework
import com.nandini.hanova.ui.components.Tab
import com.nandini.hanova.ui.components.TabBar
import com.nandini.hanova.ui.screens.ConversationScreen
import com.nandini.hanova.ui.screens.HomeScreen
import com.nandini.hanova.ui.screens.HomeworkScreen
import com.nandini.hanova.ui.screens.LectureDetailScreen
import com.nandini.hanova.ui.screens.LectureDiaryScreen
import com.nandini.hanova.ui.screens.LiveLectureScreen
import com.nandini.hanova.ui.screens.VoiceSettingsScreen
import com.nandini.hanova.ui.theme.H
import com.nandini.hanova.ui.theme.HanovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

/** Shown on Home. Change to your name (or add a settings screen later). */
private const val USER_NAME = "Nandini"

class MainActivity : ComponentActivity() {

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App is always light (paper background), so force dark status/nav icons
        // even when the phone is in dark mode
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        askMic.launch(Manifest.permission.RECORD_AUDIO)
        val container = (application as HanovaApp).container
        setContent { HanovaTheme { HanovaNav(container) } }
    }
}

@Composable
private fun HanovaNav(c: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = route in setOf(Tab.HOME.route, Tab.DIARY.route, Tab.HOMEWORK.route)

    val status by c.status.collectAsState()
    val voiceProblem by c.voiceProblem.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val summaries by remember { c.db.lectures().observeSummaries() }.collectAsState(initial = emptyList())
    val homework by remember { c.db.homework().observeAll() }.collectAsState(initial = emptyList())
    val openCount by remember { c.db.homework().observeOpenCount() }.collectAsState(initial = 0)
    var recentCourses by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(summaries.size) { recentCourses = withContext(Dispatchers.IO) { c.db.lectures().recentCourses() } }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(H.Ground)) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) {
                    HomeScreen(
                        userName = USER_NAME, status = status, openHomework = openCount,
                        recent = summaries, recentCourses = recentCourses,
                        onStartLecture = { course -> nav.navigate("live/" + URLEncoder.encode(course, "UTF-8")) },
                        onOpenTalk = { nav.navigate(Tab.TALK.route) },
                        onOpenHomework = { nav.goTab(Tab.HOMEWORK) },
                        onOpenDiary = { nav.goTab(Tab.DIARY) },
                        onOpenLecture = { nav.navigate("lecture/$it") },
                        voiceProblem = voiceProblem,
                        onOpenVoice = { nav.navigate("voice") },
                        onFixVoice = { runCatching { context.startActivity(c.voice.installVoicesIntent()) } },
                    )
                }
                composable("voice") {
                    VoiceSettingsScreen(c.voice, onBack = { nav.popBackStack() })
                }
                composable(Tab.DIARY.route) {
                    LectureDiaryScreen(summaries, onOpen = { nav.navigate("lecture/$it") })
                }
                composable(Tab.HOMEWORK.route) {
                    HomeworkScreen(
                        all = homework,
                        courses = recentCourses,
                        onToggle = { hw -> scope.launch(Dispatchers.IO) { c.db.homework().setDone(hw.id, !hw.done) } },
                        onDelete = { hw -> scope.launch(Dispatchers.IO) { c.db.homework().delete(hw.id) } },
                        onAdd = { task, course, due ->
                            scope.launch(Dispatchers.IO) {
                                c.db.homework().insert(
                                    Homework(lectureId = null, tMs = 0, course = course, en = task, zh = "",
                                        dueEpochDay = due, createdAt = System.currentTimeMillis())
                                )
                            }
                        },
                        onOpenLecture = { nav.navigate("lecture/$it") },
                    )
                }
                composable(Tab.TALK.route) {
                    ConversationScreen(onClose = { nav.popBackStack() })
                }
                composable("live/{course}", arguments = listOf(navArgument("course") { type = NavType.StringType })) { e ->
                    val course = URLDecoder.decode(e.arguments?.getString("course") ?: "Lecture", "UTF-8")
                    LiveLectureScreen(
                        course = course,
                        onFinished = { id ->
                            nav.navigate("lecture/$id") { popUpTo(Tab.HOME.route) }
                        },
                        onOpenHomework = { nav.goTab(Tab.HOMEWORK) },
                    )
                }
                composable("lecture/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: 0L
                    val lecture by remember(id) { c.db.lectures().observe(id) }.collectAsState(initial = null)
                    val lines by remember(id) { c.db.lines().observeFor(id) }.collectAsState(initial = emptyList())
                    LectureDetailScreen(lecture, lines, onBack = { nav.popBackStack() })
                }
            }
        }
        if (showTabs) {
            TabBar(current = route) { tab ->
                if (tab == Tab.TALK) nav.navigate(Tab.TALK.route) else nav.goTab(tab)
            }
        }
    }
}

private fun NavHostController.goTab(tab: Tab) = navigate(tab.route) {
    popUpTo(Tab.HOME.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}


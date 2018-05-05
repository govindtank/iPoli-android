package io.ipoli.android.common.redux

import io.ipoli.android.common.UIAction
import io.ipoli.android.common.mvi.ViewState
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.runBlocking
import org.amshove.kluent.`should be equal to`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.xdescribe

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 01/21/2018.
 */
object StateStoreSpek : Spek({

    describe("StateStore") {

        class TestState(data: Map<String, State>) : CompositeState<TestState>(data) {
            override fun createWithData(stateData: Map<String, State>): TestState {
                return TestState(stateData)
            }
        }

        class TestViewState : ViewState

        class TestAction : Action

        class TestSideEffectHandlerExecutor : SideEffectHandlerExecutor<TestState> {
            override fun execute(
                sideEffectHandler: SideEffectHandler<TestState>,
                action: Action,
                state: TestState,
                dispatcher: Dispatcher
            ) {
                runBlocking {
                    sideEffectHandler.execute(action, state, dispatcher)
                }

            }
        }

        class StopMiddleware : MiddleWare<TestState> {
            override fun execute(
                state: TestState,
                dispatcher: Dispatcher,
                action: Action
            ) = MiddleWare.Result.Stop
        }

        var executeCount = 0

        beforeEachTest {
            executeCount = 0
        }

        data class SubState(val dummy: String = "") : State

        val testReducer = object : Reducer<TestState, SubState> {

            override val stateKey
                get() = SubState::class.java.simpleName

            override fun reduce(state: TestState, subState: SubState, action: Action): SubState {
                executeCount++
                return subState
            }

            override fun defaultState() = SubState()
        }

        fun createStore(
            middleware: Set<MiddleWare<TestState>> = setOf(),
            sideEffectHandlers: Set<SideEffectHandler<TestState>> = setOf()
        ) =
            StateStore(
                initialState = TestState(mapOf(SubState::class.java.simpleName to SubState())),
                reducers = setOf(testReducer),
                sideEffectHandlers = sideEffectHandlers,
                sideEffectHandlerExecutor = TestSideEffectHandlerExecutor(),
                middleware = middleware,
                coroutineContext = Unconfined
            )

        it("should call the reducer with no middleware") {
            createStore().dispatch(TestAction())
            executeCount.`should be equal to`(1)
        }

        it("should not call reducer with stopping middleware") {
            createStore(setOf(StopMiddleware())).dispatch(TestAction())

            executeCount.`should be equal to`(0)
        }

        describe("ViewStateReducer") {

            var vsReduceCount = 0

            data class ViewAction(val data: String) : Action

            val vsReducer = object : ViewStateReducer<TestState, TestViewState> {
                override fun reduce(
                    state: TestState,
                    subState: TestViewState,
                    action: Action
                ): TestViewState {
                    if (action is ViewAction)
                        vsReduceCount++
                    return subState
                }

                override fun defaultState(): TestViewState {
                    return TestViewState()
                }

                override val stateKey: String
                    get() = TestViewState::class.java.simpleName
            }

            it("should attach and call") {
                vsReduceCount = 0
                val store = createStore()
                store.dispatch(UIAction.Attach(vsReducer))
                store.dispatch(ViewAction("test"))
                vsReduceCount.`should be equal to`(1)
            }

            it("should detach and not call") {
                vsReduceCount = 0
                val store = createStore()
                store.dispatch(UIAction.Attach(vsReducer))
                store.dispatch(UIAction.Detach(vsReducer))
                store.dispatch(ViewAction("test"))
                vsReduceCount.`should be equal to`(0)
            }
        }

        it("should call subscriber on dispatch") {

            var stateChangeCount = 0

            val subscriber = object : StateStore.StateChangeSubscriber<TestState> {
                override suspend fun onStateChanged(newState: TestState) {
                    stateChangeCount++
                }
            }

            val store = createStore()
            store.subscribe(subscriber)
            stateChangeCount = 0
            store.dispatch(TestAction())

            stateChangeCount.`should be equal to`(1)
        }

        it("should call SideEffectHandler") {


            class SideEffectAction : Action

            var sideEffectCalls = 0

            val sideEffect = object : SideEffectHandler<TestState> {
                override suspend fun execute(
                    action: Action,
                    state: TestState,
                    dispatcher: Dispatcher
                ) {
                    sideEffectCalls++
                }

                override fun canHandle(action: Action) =
                    action is SideEffectAction

            }

            val store = createStore(sideEffectHandlers = setOf(sideEffect))

            store.dispatch(SideEffectAction())

            sideEffectCalls.`should be equal to`(1)
        }

        it("should not call SideEffectHandler when it can't handle Action") {

            class SideEffectAction : Action

            var sideEffectCalls = 0

            val sideEffect = object : SideEffectHandler<TestState> {
                override suspend fun execute(
                    action: Action,
                    state: TestState,
                    dispatcher: Dispatcher
                ) {
                    sideEffectCalls++
                }

                override fun canHandle(action: Action) = false

            }

            val store = createStore(sideEffectHandlers = setOf(sideEffect))

            store.dispatch(SideEffectAction())

            sideEffectCalls.`should be equal to`(0)
        }

    }
})
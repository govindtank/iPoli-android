package io.ipoli.android.common.redux

import io.ipoli.android.common.UIAction
import io.ipoli.android.common.mvi.ViewState
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.launch
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/20/2018.
 */

interface Action

interface State

interface SideEffectHandler<in S : State> {

    suspend fun execute(action: Action, state: S, dispatcher: Dispatcher)

    fun canHandle(action: Action): Boolean
}

interface SideEffectHandlerExecutor<S : CompositeState<S>> {
    fun execute(
        sideEffectHandler: SideEffectHandler<S>,
        action: Action,
        state: S,
        dispatcher: Dispatcher
    )
}

class CoroutineSideEffectHandlerExecutor<S : CompositeState<S>>(
    private val coroutineContext: CoroutineContext
) : SideEffectHandlerExecutor<S> {
    override fun execute(
        sideEffectHandler: SideEffectHandler<S>,
        action: Action,
        state: S,
        dispatcher: Dispatcher
    ) {
        launch(coroutineContext) {
            sideEffectHandler.execute(action, state, dispatcher)
        }
    }
}

abstract class CompositeState<T>(
    private val stateData: Map<String, State>
) : State where T : CompositeState<T> {

    fun <S> stateFor(key: Class<S>): S {
        return stateFor(key.simpleName)
    }

    fun <S> stateFor(key: String): S {

        require(stateData.containsKey(key))

        val data = stateData[key]

        @Suppress("unchecked_cast")
        return data as S
    }

    fun <S> hasState(key: Class<S>): Boolean = hasState(key.simpleName)

    fun hasState(key: String): Boolean = stateData.containsKey(key)

    fun update(stateKey: String, newState: State) =
        createWithData(stateData.plus(Pair(stateKey, newState)))

    fun update(stateData: Map<String, State>) =
        createWithData(stateData.plus(stateData))

    fun remove(stateKey: String) =
        createWithData(stateData.minus(stateKey))

    val keys = stateData.keys

    protected abstract fun createWithData(stateData: Map<String, State>): T
}

interface Reducer<AS : CompositeState<AS>, S : State> {

    fun reduce(state: AS, action: Action) =
        reduce(state, state.stateFor(stateKey), action)

    fun reduce(state: AS, subState: S, action: Action): S

    fun defaultState(): S

    val stateKey: String
}

interface ViewStateReducer<S : CompositeState<S>, VS : ViewState> : Reducer<S, VS>

interface Dispatcher {
    fun <A : Action> dispatch(action: A)
}

class StateStore<S : CompositeState<S>>(
    initialState: S,
    reducers: Set<Reducer<S, *>>,
    sideEffectHandlers: Set<SideEffectHandler<S>> = setOf(),
    private val sideEffectHandlerExecutor: SideEffectHandlerExecutor<S>,
    middleware: Set<MiddleWare<S>> = setOf(),
    coroutineContext: CoroutineContext = CommonPool
) : Dispatcher {

    interface StateChangeSubscriber<in S> {
        suspend fun onStateChanged(newState: S)
    }

    private val middleWare = CompositeMiddleware(middleware)
    private val reducer = CompositeReducer()
    private val sideEffectHandlers = CopyOnWriteArraySet<SideEffectHandler<S>>(sideEffectHandlers)
    private val reducers = CopyOnWriteArraySet<Reducer<S, *>>(reducers)
    private val stateChangeSubscribers = CopyOnWriteArraySet<StateChangeSubscriber<S>>()

    private val stateActor = createStateActor(coroutineContext)

    fun addSideEffectHandler(handler: SideEffectHandler<S>) {
        sideEffectHandlers.add(handler)
    }

    fun removeSideEffectHandler(handler: SideEffectHandler<S>) {
        sideEffectHandlers.remove(handler)
    }

    var state = initialState

    private fun createStateActor(
        coroutineContext: CoroutineContext
    ) =
        actor<Action>(coroutineContext, capacity = Channel.UNLIMITED) {

            for (action in channel) {
                val res = executeMiddleware(state, action)
                if (res == MiddleWare.Result.Continue) {
                    state = applyReducers(state, action)
                    notifyStateChanged(state)
                    executeSideEffects(action, state)
                }
            }
        }

    private fun applyReducers(state: S, action: Action) =
        reducer.reduce(state, action)

    private fun executeMiddleware(
        state: S,
        action: Action
    ) = middleWare.execute(state, this, action)

    override fun <A : Action> dispatch(action: A) {
        stateActor.offer(action)
    }

    private fun executeSideEffects(action: Action, newState: S) {
        sideEffectHandlers
            .filter {
                it.canHandle(action)
            }
            .forEach {
                sideEffectHandlerExecutor.execute(it, action, newState, this)
            }
    }

    private suspend fun notifyStateChanged(newState: S) {
        stateChangeSubscribers.forEach {
            it.onStateChanged(newState)
        }
    }

    fun subscribe(subscriber: StateChangeSubscriber<S>) {
        stateChangeSubscribers.add(subscriber)
    }

    fun unsubscribe(subscriber: StateChangeSubscriber<S>) {
        stateChangeSubscribers.remove(subscriber)
    }

    inner class CompositeReducer {
        fun reduce(state: S, action: Action): S {
            if (action is UIAction.Attach<*>) {
                val stateKey = action.reducer.stateKey
                require(
                    !state.keys.contains(stateKey),
                    { "Key $stateKey is already added to the state?!" })
                val reducer = action.reducer
                @Suppress("UNCHECKED_CAST")
                reducers.add(reducer as ViewStateReducer<S, *>)
                return state.update(stateKey, reducer.defaultState())
            }

            if (action is UIAction.Detach<*>) {
                val stateKey = action.reducer.stateKey
                @Suppress("UNCHECKED_CAST")
                val reducer = action.reducer as ViewStateReducer<S, *>
                require(
                    reducers.contains(reducer),
                    { "Reducer $reducer not found in state reducers" })
                require(
                    state.keys.contains(stateKey),
                    { "State with key $stateKey not found in state" })
                reducers.remove(reducer)
                return state.remove(stateKey)
            }

            val stateToReducer = reducers.map { it.stateKey to it }.toMap()
            val newState = state.keys.map {
                val reducer = stateToReducer[it]!!
                val subState = reducer.reduce(state, action)
                it to subState
            }.toMap()

            return state.update(newState)
        }

    }
}
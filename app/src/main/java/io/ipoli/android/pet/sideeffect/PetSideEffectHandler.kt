package io.ipoli.android.pet.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.pet.PetAction
import io.ipoli.android.pet.PetItem
import io.ipoli.android.pet.PetItemType
import io.ipoli.android.pet.PetViewState
import io.ipoli.android.pet.usecase.*
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 4/29/18.
 */
object PetSideEffectHandler : AppSideEffectHandler() {

    private val comparePetItemsUseCase by required { comparePetItemsUseCase }
    private val renamePetUseCase by required { renamePetUseCase }
    private val feedPetUseCase by required { feedPetUseCase }
    private val revivePetUseCase by required { revivePetUseCase }
    private val takeOffPetItemUseCase by required { takeOffPetItemUseCase }
    private val equipPetItemUseCase by required { equipPetItemUseCase }
    private val buyPetItemUseCase by required { buyPetItemUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is PetAction.RenamePet -> {
                renamePetUseCase.execute(RenamePetUseCase.Params(action.name))
            }

            is PetAction.Feed -> {
                val result = feedPetUseCase.execute(Parameters(action.food))
                when (result) {
                    is Result.TooExpensive -> dispatch(PetAction.FoodTooExpensive)
                    is Result.PetFed -> {
                        val pet = result.player.pet
                        dispatch(PetAction.PetFed(pet, action.food, result.wasFoodTasty))
                    }
                }
            }

            is PetAction.Revive -> {
                val result = revivePetUseCase.execute(Unit)
                when (result) {
                    is RevivePetUseCase.Result.TooExpensive ->
                        dispatch(PetAction.ReviveTooExpensive)
                    else -> dispatch(PetAction.PetRevived)
                }
            }

            is PetAction.ShowItemListRequest -> {
                val cmpRes = createCompareResult(state, PetItemType.BODY_ARMOR)
                dispatch(PetAction.ShowItemList(cmpRes))
            }

            is PetAction.ShowHeadItemListRequest -> {
                val cmpRes = createCompareResult(state, PetItemType.HAT)
                dispatch(PetAction.ShowHeadItemList(cmpRes))
            }

            is PetAction.ShowFaceItemListRequest -> {
                val cmpRes = createCompareResult(state, PetItemType.MASK)
                dispatch(PetAction.ShowFaceItemList(cmpRes))
            }

            is PetAction.ShowBodyItemListRequest -> {
                val cmpRes = createCompareResult(state, PetItemType.BODY_ARMOR)
                dispatch(PetAction.ShowBodyArmorItemList(cmpRes))
            }

            is PetAction.CompareItem -> {
                val petState = state.stateFor(PetViewState::class.java)
                val equippedItem = petState.compareEquippedItem
                val cmpRes = comparePetItemsUseCase.execute(
                    ComparePetItemsUseCase.Params(
                        equippedItem,
                        action.item
                    )
                )
                dispatch(PetAction.ItemsCompared(cmpRes, action.item))
            }

            is PetAction.TakeItemOff -> {
                takeOffPetItemUseCase.execute(TakeOffPetItemUseCase.Params(action.item))
            }

            is PetAction.EquipItem -> {
                equipPetItemUseCase.execute(EquipPetItemUseCase.Params(action.item))
            }

            is PetAction.BuyItem -> {
                val result = buyPetItemUseCase.execute(BuyPetItemUseCase.Params(action.item))
                when (result) {
                    is BuyPetItemUseCase.Result.TooExpensive ->
                        dispatch(PetAction.ItemTooExpensive)
                    is BuyPetItemUseCase.Result.ItemBought ->
                        dispatch(PetAction.ItemBought)
                }
            }
        }
    }


    private fun createCompareResult(
        state: AppState,
        itemType: PetItemType
    )
        : ComparePetItemsUseCase.Result {
        val equipment = state.dataState.player!!.pet.equipment
        val equipped = when (itemType) {
            PetItemType.HAT -> equipment.hat
            PetItemType.MASK -> equipment.mask
            PetItemType.BODY_ARMOR -> equipment.bodyArmor
        }

        return comparePetItemsUseCase.execute(
            ComparePetItemsUseCase.Params(
                equipped,
                PetItem.values().first { it.type == itemType }
            )
        )
    }


    override fun canHandle(action: Action) =
        action is PetAction

}
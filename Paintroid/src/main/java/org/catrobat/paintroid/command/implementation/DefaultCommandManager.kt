/*
 * Paintroid: An image manipulation application for Android.
 * Copyright (C) 2010-2021 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.paintroid.command.implementation

import org.catrobat.paintroid.command.Command
import org.catrobat.paintroid.command.CommandManager
import org.catrobat.paintroid.command.CommandManager.CommandListener
import org.catrobat.paintroid.common.CommonFactory
import org.catrobat.paintroid.contract.LayerContracts
import org.catrobat.paintroid.model.CommandManagerModel
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque
import kotlin.collections.ArrayList

class DefaultCommandManager(
    private val commonFactory: CommonFactory,
    private val layerModel: LayerContracts.Model
) : CommandManager {
    private val commandListeners: MutableList<CommandListener> = ArrayList()
    private val redoCommandList: Deque<Command> = ArrayDeque()
    private val undoCommandList: Deque<Command> = ArrayDeque()
    private var initialStateCommand: Command? = null

    override val isBusy: Boolean
        get() = false

    override val isUndoAvailable: Boolean
        get() = !undoCommandList.isEmpty()

    override val isRedoAvailable: Boolean
        get() = !redoCommandList.isEmpty()

    override val commandManagerModel: CommandManagerModel?
        get() {
            val commandList = ArrayList<Command>()
            val it = redoCommandList.descendingIterator()
            while (it.hasNext()) {
                commandList.add(it.next())
            }
            commandList.addAll(undoCommandList)
            var model: CommandManagerModel? = null
            initialStateCommand?.let { initialCommand ->
                model = CommandManagerModel(initialCommand, commandList)
            }
            return model
        }

    override fun addCommandListener(commandListener: CommandListener) {
        commandListeners.add(commandListener)
    }

    override fun removeCommandListener(commandListener: CommandListener) {
        commandListeners.remove(commandListener)
    }

    override fun addCommand(command: Command?) {
        redoCommandList.clear()
        command?.let { undoCommandList.addFirst(it) }
        val currentLayer = layerModel.currentLayer
        val canvas = commonFactory.createCanvas()
        canvas.setBitmap(currentLayer?.bitmap)
        command?.run(canvas, layerModel)
        notifyCommandExecuted()
    }

    override fun loadCommandsCatrobatImage(model: CommandManagerModel?) {
        model ?: return
        setInitialStateCommand(model.initialCommand)
        reset()
        for (command in model.commands) {
            addCommand(command)
        }
    }

    override fun undo() {
        val command = undoCommandList.pop()
        redoCommandList.addFirst(command)

        var layerCount = layerModel.layerCount
        val currentCommandName = command.javaClass.simpleName
        val addLayerCommandRegex = AddLayerCommand::class.java.simpleName.toRegex()
        val mergeLayerCommandRegex = MergeLayersCommand::class.java.simpleName.toRegex()

        if (currentCommandName.matches(addLayerCommandRegex)) {
            layerCount--
            layerModel.removeLayerAt(0)
        }

        val checkBoxes: MutableList<Boolean> = ArrayList(Collections.nCopies(layerCount, true))

        if (!currentCommandName.matches(mergeLayerCommandRegex)) {
            backUpCheckBoxes(layerCount, checkBoxes)
        }

        layerModel.reset()

        val canvas = commonFactory.createCanvas()

        initialStateCommand?.run(canvas, layerModel)

        val iterator = undoCommandList.descendingIterator()
        while (iterator.hasNext()) {
            val currentLayer = layerModel.currentLayer
            canvas.setBitmap(currentLayer?.bitmap)
            iterator.next().run(canvas, layerModel)
        }

        if (!currentCommandName.matches(mergeLayerCommandRegex)) {
            fetchBackCheckBoxes(layerCount, checkBoxes)
        }

        notifyCommandExecuted()
    }

    override fun redo() {
        val command = redoCommandList.pop()
        undoCommandList.addFirst(command)

        val currentLayer = layerModel.currentLayer
        val canvas = commonFactory.createCanvas()
        if (currentLayer != null) {
            if (currentLayer.checkBox) {
                canvas.setBitmap(currentLayer.bitmap)
            } else {
                canvas.setBitmap(currentLayer.transparentBitmap)
            }
        }

        command.run(canvas, layerModel)
        notifyCommandExecuted()
    }

    override fun reset() {
        undoCommandList.clear()
        redoCommandList.clear()
        layerModel.reset()

        if (initialStateCommand != null) {
            val canvas = commonFactory.createCanvas()
            initialStateCommand?.run(canvas, layerModel)
        }

        notifyCommandExecuted()
    }

    override fun shutdown() = Unit

    override fun setInitialStateCommand(command: Command) {
        initialStateCommand = command
    }

    private fun notifyCommandExecuted() {
        for (listener in commandListeners) {
            listener.commandPostExecute()
        }
    }

    private fun backUpCheckBoxes(layerCount: Int, checkBoxes: MutableList<Boolean>) {
        if (layerCount > 1) {
            for (index in layerCount - 1 downTo 0) {
                checkBoxes[index] = layerModel.getLayerAt(index).checkBox
            }
        } else {
            checkBoxes[0] = layerModel.getLayerAt(0).checkBox
        }
    }

    private fun fetchBackCheckBoxes(layerCount: Int, checkBoxes: List<Boolean>) {
        if (layerCount > 1) {
            for (index in layerCount - 1 downTo 0) {
                val destinationLayer = layerModel.getLayerAt(index)
                if (!checkBoxes[index]) {
                    destinationLayer.switchBitmaps(false)
                    destinationLayer.checkBox = false
                }
            }
        } else {
            val destinationLayer = layerModel.currentLayer
            if (destinationLayer != null && !checkBoxes[0]) {
                destinationLayer.switchBitmaps(false)
                destinationLayer.checkBox = false
            }
        }
    }
}

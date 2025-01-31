/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2021. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.client.gui;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.systems.RenderSystem;
import dan200.computercraft.shared.peripheral.printer.ContainerPrinter;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GuiPrinter extends HandledScreen<ContainerPrinter> {
    private static final Identifier BACKGROUND = new Identifier("computercraft", "textures/gui/printer.png");

    public GuiPrinter(ContainerPrinter container, PlayerInventory player, Text title) {
        super(container, player, title);
    }

    /*@Override
    protected void drawGuiContainerForegroundLayer( int mouseX, int mouseY )
    {
        String title = getTitle().getFormattedText();
        font.drawString( title, (xSize - font.getStringWidth( title )) / 2.0f, 6, 0x404040 );
        font.drawString( I18n.format( "container.inventory" ), 8, ySize - 96 + 2, 0x404040 );
    }*/

    @Override
    public void render(@Nonnull MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        this.drawMouseoverTooltip(stack, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(@Nonnull MatrixStack transform, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.client.getTextureManager()
                   .bindTexture(BACKGROUND);
        this.drawTexture(transform, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        if (this.getScreenHandler().isPrinting()) {
            this.drawTexture(transform, this.x + 34, this.y + 21, 176, 0, 25, 45);
        }
    }
}

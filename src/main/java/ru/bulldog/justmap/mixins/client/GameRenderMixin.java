package ru.bulldog.justmap.mixins.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.bulldog.justmap.client.render.WaypointRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRenderMixin {

	@Final
	@Shadow
	private Camera camera;

	@Shadow
	protected abstract double getFov(Camera camera, float f, boolean bl);

//	@Inject(method = "render", at = @At(value = "TAIL"), locals = LocalCapture.CAPTURE_FAILHARD)
//	private void renderHUD(float tickDelta, long startTime, boolean tick, CallbackInfo ci, boolean tick2, int i, int j, Window window, Matrix4f matrix4f, MatrixStack matrixStack, DrawContext drawContext) {
//		WaypointRenderer.renderHUD(drawContext, tickDelta, (float) this.getFov(camera, tickDelta, true));
//	}


	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;F)V"), locals = LocalCapture.CAPTURE_FAILEXCEPTION)
	private void onBeforeRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int i, int j, Window window, Matrix4f matrix4f, MatrixStack matrixStack, DrawContext drawContext) {
		// Store the screen in a variable in case someone tries to change the screen during this before render event.
		// If someone changes the screen, the after render event will likely have class cast exceptions or an NPE.
		WaypointRenderer.renderHUD(drawContext, tickDelta, (float) this.getFov(camera, tickDelta, true));
	}
//	Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError:
//		LVT in net/minecraft/client/render/GameRenderer::render(FJZ)V
//		has incompatible changes at opcode
//		596 in callback justmap.mixins.client.json:GameRenderMixin from mod justmap
//		->@Inject::renderHUD(FJZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;ZIILnet/minecraft/client/util/Window;Lorg/joml/Matrix4f;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/gui/DrawContext;)V.

}
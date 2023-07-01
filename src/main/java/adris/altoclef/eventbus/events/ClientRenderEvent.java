package adris.altoclef.eventbus.events;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class ClientRenderEvent {
    public MatrixStack stack;
    public float tickDelta;

    public VertexConsumerProvider vertexConsumerProvider;

    public ClientRenderEvent(MatrixStack stack, float tickDelta, VertexConsumerProvider vertexConsumerProvider) {
        this.stack = stack;
        this.tickDelta = tickDelta;
        this.vertexConsumerProvider = vertexConsumerProvider;
    }
}

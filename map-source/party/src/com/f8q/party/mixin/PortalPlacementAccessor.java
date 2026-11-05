package com.f8q.party.mixin;

import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the actual generated placement, not an assumption based on portal structure ID. */
@Mixin(RuinedPortalPiece.class)
public interface PortalPlacementAccessor {
    @Accessor("verticalPlacement")
    RuinedPortalPiece.VerticalPlacement f8q$getPlacement();
}

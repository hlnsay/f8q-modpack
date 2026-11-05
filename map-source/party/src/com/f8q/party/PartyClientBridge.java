package com.f8q.party;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Keeps client classes out of the server's reach. */
final class PartyClientBridge {
    private PartyClientBridge() {
    }

    static void offer(Net.Offer offer) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PartyClient.offer(offer);
        }
    }

    static void helpers(Net.Helpers helpers) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PartyClient.helpers(helpers);
        }
    }

    static void state(Net.State state) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PartyClient.state(state);
        }
    }

    static void marks(Net.MarksDown marks) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PartyClient.marks(marks);
        }
    }

    static void unknowns(Net.Unknowns unknowns) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PartyClient.unknowns(unknowns);
        }
    }
}

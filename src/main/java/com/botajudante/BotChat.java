package com.botajudante;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class BotChat {

    private static void reply(ServerPlayerEntity player, String text) {
        player.sendMessage(Text.literal("<Bot> " + text), false);
    }

    public static void onChat(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        String raw = message.getSignedContent();
        String text = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        if (!text.matches("bot([\\s,.:;!?].*)?")) {
            return;
        }
        String cmd = text.substring(3).replaceAll("[,.:;!?]", " ").trim();

        World world = sender.getWorld();
        List<HelperEntity> bots = world.getEntitiesByClass(
                HelperEntity.class,
                sender.getBoundingBox().expand(64.0),
                b -> b.isOwner(sender));

        if (bots.isEmpty()) {
            reply(sender, "Não estou por perto. Chegue mais perto de mim.");
            return;
        }
        bots.sort(Comparator.comparingDouble((HelperEntity b) -> b.squaredDistanceTo(sender)));
        HelperEntity bot = bots.get(0);

        if (cmd.isEmpty() || cmd.equals("oi") || cmd.equals("ola") || cmd.equals("ajuda")
                || cmd.equals("comandos") || cmd.equals("help")) {
            reply(sender, "Oi! Comandos: 'bot madeira' (corto árvores), 'bot minerar' (cavo um túnel), "
                    + "'bot casa' (construo uma casa), 'bot itens' (mostro o que carrego), "
                    + "'bot entregar' (te dou os itens), 'bot seguir' (volto a te seguir), "
                    + "'bot ficar' (fico parado).");
        } else if (cmd.contains("entreg")) {
            int total = bot.deliverTo(sender);
            reply(sender, total == 0 ? "Não tenho nada para entregar." : "Pronto! Te entreguei " + total + " itens.");
        } else if (cmd.contains("iten") || cmd.contains("inventario") || cmd.contains("mochila")) {
            reply(sender, bot.describeInventory());
        } else if (cmd.contains("seguir") || cmd.contains("segue") || cmd.contains("parar") || cmd.contains("pare")) {
            bot.wakeUp();
            bot.setMode(HelperEntity.Mode.FOLLOW);
            reply(sender, "Ok, estou te seguindo.");
        } else if (cmd.contains("casa") || cmd.contains("construi")) {
            if (bot.getMode() == HelperEntity.Mode.BUILD) {
                reply(sender, "Já estou construindo!");
                return;
            }
            Direction facing = sender.getHorizontalFacing();
            BlockPos ahead = sender.getBlockPos().offset(facing, 5);
            int groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ahead.getX(), ahead.getZ());
            BlockPos center = new BlockPos(ahead.getX(), groundY, ahead.getZ());
            bot.wakeUp();
            bot.setBuildSite(center, facing);
            bot.setMode(HelperEntity.Mode.BUILD);
            reply(sender, "Vou construir uma casa 5x5 a 5 blocos à sua frente. Saia da frente!");
        } else if (cmd.contains("madeira") || cmd.contains("lenha") || cmd.contains("arvore") || cmd.contains("cortar")) {
            bot.wakeUp();
            bot.setMode(HelperEntity.Mode.WOOD);
            reply(sender, "Vou cortar árvores. Diga 'bot seguir' quando quiser que eu pare.");
        } else if (cmd.contains("miner") || cmd.contains("tunel") || cmd.contains("cavar")) {
            bot.wakeUp();
            bot.setMineDirection(sender.getHorizontalFacing());
            bot.setMode(HelperEntity.Mode.MINE);
            reply(sender, "Vou cavar um túnel na direção em que você está olhando, a partir de onde estou.");
        } else if (cmd.contains("fica") || cmd.contains("senta")) {
            bot.setMode(HelperEntity.Mode.FOLLOW);
            bot.sitDown();
            reply(sender, "Ok, fico aqui.");
        } else {
            reply(sender, "Não entendi. Escreva 'bot ajuda' para ver os comandos.");
        }
    }
}

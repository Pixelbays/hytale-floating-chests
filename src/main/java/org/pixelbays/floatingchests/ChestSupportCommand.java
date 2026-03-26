package org.pixelbays.floatingchests;

import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

public class ChestSupportCommand extends CommandBase {

    private final ChestSupportPatchService service;

    public ChestSupportCommand(ChestSupportPatchService service) {
        super("chestsupport", "Manage runtime support overrides for floating chest blocks.");
        this.service = service;
        this.addAliases("floatingchestsupport");
        this.addSubCommand(new IncludeCommand(service));
        this.addSubCommand(new ExcludeCommand(service));
        this.addSubCommand(new ListCommand(service));
        this.addSubCommand(new StatusCommand(service));
        this.addSubCommand(new ReapplyCommand(service));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ChestSupportPatchService.ChestSupportStateSnapshot snapshot = this.service.snapshot();
        ctx.sendMessage(Message.translation("floatingchest.command.summary")
                .param("automatic", snapshot.automaticCount())
                .param("included", snapshot.includedCount())
                .param("excluded", snapshot.excludedCount())
                .param("effective", snapshot.effectiveCount())
                .param("applied", snapshot.appliedCount()));
    }

    private static final class IncludeCommand extends CommandBase {

        IncludeCommand(ChestSupportPatchService service) {
            super("include", "Manage manual include overrides for chest support patching.");
            this.addSubCommand(new IncludeAddCommand(service));
            this.addSubCommand(new IncludeRemoveCommand(service));
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.translation("floatingchest.command.include.help"));
        }
    }

    private static final class IncludeAddCommand extends CommandBase {

        private final ChestSupportPatchService service;
        private final RequiredArg<String> blockIdArg;

        IncludeAddCommand(ChestSupportPatchService service) {
            super("add", "Always patch a block id even when it is not auto-detected.");
            this.service = service;
            this.blockIdArg = this.withRequiredArg("block_id", "The block id to force-include.", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ChestSupportPatchService.UpdateResult result = this.service.addIncludedBlockId(this.blockIdArg.get(ctx));
            ctx.sendMessage(Message.translation(result.changed()
                            ? "floatingchest.command.include.added"
                            : "floatingchest.command.include.unchanged")
                    .param("block", result.blockId())
                    .param("patched", result.patchedCount()));
        }
    }

    private static final class IncludeRemoveCommand extends CommandBase {

        private final ChestSupportPatchService service;
        private final RequiredArg<String> blockIdArg;

        IncludeRemoveCommand(ChestSupportPatchService service) {
            super("remove", "Remove a block id from the manual include list.");
            this.service = service;
            this.blockIdArg = this.withRequiredArg("block_id", "The block id to remove from force-include.", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ChestSupportPatchService.UpdateResult result = this.service.removeIncludedBlockId(this.blockIdArg.get(ctx));
            ctx.sendMessage(Message.translation(result.changed()
                            ? "floatingchest.command.include.removed"
                            : "floatingchest.command.include.not-present")
                    .param("block", result.blockId())
                    .param("patched", result.patchedCount()));
        }
    }

    private static final class ExcludeCommand extends CommandBase {

        ExcludeCommand(ChestSupportPatchService service) {
            super("exclude", "Manage manual exclude overrides for chest support patching.");
            this.addSubCommand(new ExcludeAddCommand(service));
            this.addSubCommand(new ExcludeRemoveCommand(service));
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.translation("floatingchest.command.exclude.help"));
        }
    }

    private static final class ExcludeAddCommand extends CommandBase {

        private final ChestSupportPatchService service;
        private final RequiredArg<String> blockIdArg;

        ExcludeAddCommand(ChestSupportPatchService service) {
            super("add", "Never patch a block id even when it is auto-detected.");
            this.service = service;
            this.blockIdArg = this.withRequiredArg("block_id", "The block id to exclude.", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ChestSupportPatchService.UpdateResult result = this.service.addExcludedBlockId(this.blockIdArg.get(ctx));
            ctx.sendMessage(Message.translation(result.changed()
                            ? "floatingchest.command.exclude.added"
                            : "floatingchest.command.exclude.unchanged")
                    .param("block", result.blockId())
                    .param("patched", result.patchedCount()));
        }
    }

    private static final class ExcludeRemoveCommand extends CommandBase {

        private final ChestSupportPatchService service;
        private final RequiredArg<String> blockIdArg;

        ExcludeRemoveCommand(ChestSupportPatchService service) {
            super("remove", "Remove a block id from the manual exclude list.");
            this.service = service;
            this.blockIdArg = this.withRequiredArg("block_id", "The block id to remove from exclude.", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ChestSupportPatchService.UpdateResult result = this.service.removeExcludedBlockId(this.blockIdArg.get(ctx));
            ctx.sendMessage(Message.translation(result.changed()
                            ? "floatingchest.command.exclude.removed"
                            : "floatingchest.command.exclude.not-present")
                    .param("block", result.blockId())
                    .param("patched", result.patchedCount()));
        }
    }

    private static final class ListCommand extends CommandBase {

        private final ChestSupportPatchService service;

        ListCommand(ChestSupportPatchService service) {
            super("list", "List the current automatic and manual chest support overrides.");
            this.service = service;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ChestSupportPatchService.ChestSupportStateSnapshot snapshot = this.service.snapshot();
            ctx.sendMessage(Message.translation("floatingchest.command.list.summary")
                    .param("automatic", snapshot.automaticCount())
                    .param("included", snapshot.includedCount())
                    .param("excluded", snapshot.excludedCount())
                    .param("effective", snapshot.effectiveCount())
                    .param("applied", snapshot.appliedCount()));
            ctx.sendMessage(Message.translation("floatingchest.command.list.includes")
                    .param("blocks", renderBlockIds(snapshot.includedBlockIds())));
            ctx.sendMessage(Message.translation("floatingchest.command.list.excludes")
                    .param("blocks", renderBlockIds(snapshot.excludedBlockIds())));
            ctx.sendMessage(Message.translation("floatingchest.command.list.applied")
                    .param("blocks", renderBlockIds(snapshot.appliedBlockIds())));
        }
    }

    private static final class StatusCommand extends CommandBase {

        private final ChestSupportPatchService service;
        private final RequiredArg<String> blockIdArg;

        StatusCommand(ChestSupportPatchService service) {
            super("status", "Show whether a block id is auto-matched, included, excluded, and applied.");
            this.service = service;
            this.blockIdArg = this.withRequiredArg("block_id", "The block id to inspect.", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ChestSupportPatchService.BlockStatus status = this.service.status(this.blockIdArg.get(ctx));
            ctx.sendMessage(Message.translation("floatingchest.command.status")
                    .param("block", status.blockId())
                    .param("exists", status.blockExists())
                    .param("automatic", status.automatic())
                    .param("included", status.included())
                    .param("excluded", status.excluded())
                    .param("effective", status.effective())
                    .param("applied", status.applied()));
        }
    }

    private static final class ReapplyCommand extends CommandBase {

        private final ChestSupportPatchService service;

        ReapplyCommand(ChestSupportPatchService service) {
            super("reapply", "Re-scan the item asset store and rebuild the runtime overrides.");
            this.service = service;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            int patchedCount = this.service.reapplyAll("manual reapply command");
            ctx.sendMessage(Message.translation("floatingchest.command.reapply")
                    .param("patched", patchedCount));
        }
    }

    private static String renderBlockIds(Set<String> blockIds) {
        return blockIds.isEmpty() ? "-" : String.join(", ", blockIds);
    }
}
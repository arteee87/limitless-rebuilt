package limitless_rebuilt.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantingConflicts;
import limitless_rebuilt.enchantment.EnchantmentUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enchantment table candidates.
 *
 * <p>1.19.3 rewrote the body of {@code getPossibleEntries} so that it walked the levels upwards and
 * kept the highest one that fitted; 1.21.4 keeps the same result but walks downwards from
 * {@code getMaxLevel()}, which the mod raises to {@link Integer#MAX_VALUE}. That loop is what this
 * overwrite replaces — see {@link EnchantmentUtil#highestSuitableLevel} for the bounds.
 *
 * <p>What is left here is the re-enchanting of enchanted books: vanilla only treats a plain
 * {@link Items#BOOK} as a wildcard target.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Overwrite
	public static List<EnchantmentInstance> getAvailableEnchantmentResults(
			int level,
			ItemStack stack,
			Stream<Holder<Enchantment>> possibleEnchantments) {
		List<EnchantmentInstance> entries = new ArrayList<>();
		boolean book = stack.is(Items.BOOK);

		possibleEnchantments
				.filter(entry -> entry.value().isPrimaryItem(stack) || book)
				.forEach(entry -> EnchantmentUtil.highestSuitableLevel(level, entry, entries));

		return entries;
	}

	@Redirect(
			method = "getAvailableEnchantmentResults",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private static boolean treatEnchantedBookAsBook(ItemStack stack, Object item) {
		if (item == Items.BOOK && stack.is(holder -> holder.value() == Items.ENCHANTED_BOOK)
				&& Configuration.instance.enchantment.reenchanting.allowEnchantedBooks()) {
			return true;
		}

		return stack.is(holder -> holder.value() == item);
	}

	/**
	 * Makes an enchanted book answer the one question vanilla asks before it offers anything.
	 *
	 * <p>{@code generateEnchantments} opens with a direct read of the {@code enchantable} component and
	 * returns an empty list when it is absent — before {@link #getAvailableEnchantmentResults} is ever reached. That
	 * read is the third one guarding this feature and the reason the previous two fixes looked like
	 * they had worked and were not: the table offered its three laps at full levels and the buttons did
	 * nothing, because there was nothing to put in them.
	 *
	 * <p>So the {@code enchantable} component has three readers that must agree, and they do not by
	 * default: {@link net.minecraft.world.item.ItemStack#isEnchantable()} is answered for the book by
	 * {@code ItemStackMixin}, {@code EnchantingBlocks#calculateRequiredExperienceLevel} by the shared
	 * predicate, and this one by the handler below. All three now read
	 * {@link EnchantmentUtil#isEnchantable}.
	 *
	 * <p>The value handed back is {@code 1}, which is what 1.21.4's deleted hook put on the stack. It is
	 * read twice more below, as {@code EnchantableComponent#value()}, and only widens the level by one
	 * per call.
	 *
	 * <p><b>Why the receiver is named and not the interface.</b> {@code ItemStack} inherits
	 * {@code get} from {@code ComponentsAccess} rather than declaring it, so the declaring class and
	 * the owner in the constant pool are two different things. Mixin matches an injection point against
	 * the <em>instruction's</em> owner, which is the receiver, so this has to name {@code ItemStack}:
	 * the refmap entry then reads {@code class_1799#method_58694}, which is what the call site says.
	 * Naming the interface instead produces {@code class_9473#method_58694}, and that is what took the
	 * game down — "Scanned 0 target(s)", and with {@code defaultRequire} a crash on world load, because
	 * {@code EnchantmentHelper} is first loaded by an unrelated entity ticking.
	 *
	 * <p>The annotation processor cannot resolve the method on {@code ItemStack} and keeps the name it
	 * derived from the interface. That part is fine: the name is right, and the owner is the one written
	 * here. {@code check-mixins.py} used to reject this, because it verified that the intermediary class
	 * <em>declares</em> the member and did not walk interfaces; it does now.
	 *
	 * <p>{@code ItemStack#get} is invoked exactly once in {@code generateEnchantments} and this is that
	 * call, so no {@code ordinal} is needed. The handler returns {@code Object} rather than
	 * {@code EnchantableComponent} so its erased descriptor matches the erased target method.
	 */
	@Redirect(
			method = "selectEnchantment",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
			)
	)
	private static Object treatEnchantedBookAsEnchantable(ItemStack stack, DataComponentType<?> type) {
		Object present = stack.get(type);

		if (present == null && type == DataComponents.ENCHANTABLE && EnchantmentUtil.isEnchantable(stack)) {
			return new Enchantable(1);
		}

		return present;
	}

	/**
	 * Vanilla drops every enchantment conflicting with the one it just picked. Three behaviours are
	 * possible, and they are read in this order:
	 *
	 * <ul>
	 * <li>{@code conflicts.generate} keeps everything except the pick itself, so a lap can offer
	 * several mutually exclusive enchantments at once.
	 * <li>{@code conflicts.merge} keeps everything. An offer the player cannot apply is no offer, and
	 * applying is already permitted by that same switch — the anvil and
	 * {@link EnchantmentUtil#mergeEnchantment} both key off it — so the lap and the application would
	 * otherwise disagree about what is allowed.
	 * <li>Neither, and vanilla decides.
	 * </ul>
	 *
	 * <p>The second branch does nothing on purpose: keeping the conflicting candidates <em>is</em> the
	 * whole change, and the call it replaces would be the only thing removing them.
	 */
	@Redirect(
			method = "selectEnchantment",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;filterCompatibleEnchantments(Ljava/util/List;Lnet/minecraft/world/item/enchantment/EnchantmentInstance;)V"
			)
	)
	private static void keepConflicts(List<EnchantmentInstance> possibleEntries, EnchantmentInstance picked) {
		EnchantingConflicts conflicts = Configuration.instance.enchantment.conflicts;

		if (conflicts.generate) {
			possibleEntries.removeIf(entry -> !entry.enchantment().equals(picked.enchantment()));
		} else if (!conflicts.merge) {
			EnchantmentHelper.filterCompatibleEnchantments(possibleEntries, picked);
		}
	}
}
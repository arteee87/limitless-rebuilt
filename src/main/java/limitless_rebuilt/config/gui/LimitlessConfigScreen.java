package limitless_rebuilt.config.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import limitless_rebuilt.Limitless;
import limitless_rebuilt.config.Configuration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentTooltipConfiguration;
import limitless_rebuilt.enchantment.ExperienceUtil;
import limitless_rebuilt.config.anvil.AnvilConfiguration;
import limitless_rebuilt.config.common.CostDisplay;
import limitless_rebuilt.config.enchantment.EnchantingBlockConfiguration;
import limitless_rebuilt.config.enchantment.EnchantmentConfiguration;
import limitless_rebuilt.config.enchantment.entry.EnchantmentEntry;
import limitless_rebuilt.config.enchantment.entry.radius.Radius;
import limitless_rebuilt.enchantment.EnchantingBlocks;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Configuration screen.
 *
 * <p>1.19.3 built this with AutoConfig, whose widget set and layout the mod relied on; the port keeps
 * the same options and the same translation keys and lays them out with plain cloth config
 * categories and collapsible sub categories instead.
 *
 * <p>{@code ConfigBuilder#entryBuilder} hands out an unbound builder: every entry, including the
 * built sub categories, has to be attached to its category with {@link ConfigCategory#addEntry} or
 * the tab renders empty.
 */
@Environment(EnvType.CLIENT)
public final class LimitlessConfigScreen {
	private LimitlessConfigScreen() {
	}

	public static Screen create(Screen parent) {
		Configuration configuration = Configuration.instance;

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("text.autoconfig.limitless-rebuilt.title"))
				.setSavingRunnable(Configuration::save);

		ConfigEntryBuilder entries = builder.entryBuilder();

		generalCategory(builder, entries, configuration);
		enchantmentCategory(builder, entries, configuration.enchantment);
		anvilCategory(builder, entries, configuration.anvil);

		return builder.build();
	}

	private static void generalCategory(ConfigBuilder builder, ConfigEntryBuilder entries, Configuration configuration) {
		ConfigCategory category = builder.getOrCreateCategory(
				Component.translatable("text.autoconfig.limitless-rebuilt.category.default"));

		category.addEntry(toggle(entries, "automaticReloading", configuration.automaticReloading,
				value -> configuration.automaticReloading = value));
		category.addEntry(toggle(entries, "debug", configuration.debug,
				value -> configuration.debug = value));
		category.addEntry(entries.startTextDescription(
				Component.translatable("text.autoconfig.limitless-rebuilt.option.automaticReloading.@Tooltip")).build());
	}

	private static void enchantmentCategory(
			ConfigBuilder builder,
			ConfigEntryBuilder entries,
			EnchantmentConfiguration configuration) {
		ConfigCategory category = builder.getOrCreateCategory(
				Component.translatable("text.autoconfig.limitless-rebuilt.category.enchantment"));

		category.addEntry(field(entries, "enchantment.globalMaxLevel", configuration.globalMaxLevel, 1, Integer.MAX_VALUE,
				value -> configuration.globalMaxLevel = value));
		category.addEntry(toggle(entries, "enchantment.allowTreasure", configuration.allowTreasure,
				value -> configuration.allowTreasure = value));
		category.addEntry(toggle(entries, "enchantment.revealEnchantments", configuration.revealEnchantments,
				value -> configuration.revealEnchantments = value));
		category.addEntry(field(entries, "enchantment.maxLevelRequirement",
				configuration.maxLevelRequirement, 0, ExperienceUtil.VANILLA_MAX_REQUIREMENT,
				value -> configuration.maxLevelRequirement = value));

		category.addEntry(conflictsSection(entries, configuration));
		category.addEntry(enchantmentNormalizationSection(entries, configuration));
		category.addEntry(reenchantingSection(entries, configuration));
		category.addEntry(tooltipSection(entries, configuration.tooltip));
		category.addEntry(blocksSection(entries, configuration.enchantingBlocks));
		category.addEntry(particlesSection(entries, configuration));
		category.addEntry(perEnchantmentSection(entries, configuration));
	}

	private static AbstractConfigListEntry conflictsSection(ConfigEntryBuilder entries, EnchantmentConfiguration configuration) {
		SubCategoryBuilder section = section(entries, "enchantment.conflicts");

		section.add(toggle(entries, "enchantment.conflicts.generate", configuration.conflicts.generate,
				value -> configuration.conflicts.generate = value));
		section.add(toggle(entries, "enchantment.conflicts.merge", configuration.conflicts.merge,
				value -> configuration.conflicts.merge = value));

		return section.build();
	}

	private static AbstractConfigListEntry enchantmentNormalizationSection(
			ConfigEntryBuilder entries,
			EnchantmentConfiguration configuration) {
		SubCategoryBuilder section = section(entries, "enchantment.normalization");

		section.add(toggle(entries, "enchantment.normalization.enabled", configuration.normalization.enabled,
				value -> configuration.normalization.enabled = value,
				"enchantment.normalization.enabled.@Tooltip"));
		section.add(field(entries, "enchantment.normalization.offset", configuration.normalization.offset, 0, 200,
				value -> configuration.normalization.offset = value,
				"enchantment.normalization.offset.@Tooltip"));

		return section.build();
	}

	private static AbstractConfigListEntry reenchantingSection(
			ConfigEntryBuilder entries,
			EnchantmentConfiguration configuration) {
		SubCategoryBuilder section = section(entries, "enchantment.reenchanting");

		section.add(toggle(entries, "enchantment.reenchanting.enabled", configuration.reenchanting.enabled,
				value -> configuration.reenchanting.enabled = value));
		section.add(toggle(entries, "enchantment.reenchanting.removeConflicts",
				configuration.reenchanting.removeConflicts,
				value -> configuration.reenchanting.removeConflicts = value));
		section.add(toggle(entries, "enchantment.reenchanting.allowEnchantedBooks",
				configuration.reenchanting.allowEnchantedBooks,
				value -> configuration.reenchanting.allowEnchantedBooks = value));
		section.add(toggle(entries, "enchantment.reenchanting.allowEquipment",
				configuration.reenchanting.allowEquipment,
				value -> configuration.reenchanting.allowEquipment = value));

		return section.build();
	}

	private static AbstractConfigListEntry tooltipSection(
			ConfigEntryBuilder entries,
			EnchantmentTooltipConfiguration tooltip) {
		SubCategoryBuilder section = section(entries, "enchantment.tooltip");

		section.add(toggle(entries, "enchantment.tooltip.enabled", tooltip.enabled,
				value -> tooltip.enabled = value));
		section.add(toggle(entries, "enchantment.tooltip.showDamage", tooltip.showDamage,
				value -> tooltip.showDamage = value));
		section.add(toggle(entries, "enchantment.tooltip.showKnockback", tooltip.showKnockback,
				value -> tooltip.showKnockback = value));
		section.add(toggle(entries, "enchantment.tooltip.showProtection", tooltip.showProtection,
				value -> tooltip.showProtection = value));

		return section.build();
	}

	private static AbstractConfigListEntry blocksSection(ConfigEntryBuilder entries, EnchantingBlockConfiguration blocks) {
		SubCategoryBuilder section = section(entries, "enchantment.enchantingBlocks");

		section.add(field(entries, "enchantment.enchantingBlocks.maxBlocks", blocks.maxBlocks, 1, 4096,
				value -> blocks.maxBlocks = value));
		section.add(field(entries, "enchantment.enchantingBlocks.maxPower", blocks.maxPower, 0, Integer.MAX_VALUE,
				value -> blocks.maxPower = value));
		section.add(radiusSection(entries, blocks.radius, "enchantment.enchantingBlocks.radius").build());

		List<String> whitelist = new ArrayList<>(blocks.whitelist.keySet());
		List<String> blacklist = new ArrayList<>(blocks.blacklist);

		section.add(entries.startStrList(
						Component.translatable("text.autoconfig.limitless-rebuilt.option.enchantment.enchantingBlocks.whitelist"), whitelist)
				.setDefaultValue(whitelist)
				.setSaveConsumer(values -> {
					blocks.whitelist = Map.copyOf(parse(values, blocks.whitelist));
					EnchantingBlocks.reload();
				})
				.build());

		section.add(entries.startStrList(
						Component.translatable("text.autoconfig.limitless-rebuilt.option.enchantment.enchantingBlocks.blacklist"), blacklist)
				.setDefaultValue(blacklist)
				.setSaveConsumer(values -> {
					blocks.blacklist = List.copyOf(values);
					EnchantingBlocks.reload();
				})
				.build());

		return section.build();
	}

	private static AbstractConfigListEntry particlesSection(
			ConfigEntryBuilder entries,
			EnchantmentConfiguration configuration) {
		SubCategoryBuilder section = section(entries, "enchantment.particles");

		section.add(toggle(entries, "enchantment.particles.enabled", configuration.particles.enabled,
				value -> configuration.particles.enabled = value));
		section.add(toggle(entries, "enchantment.particles.inherit", configuration.particles.inherit,
				value -> configuration.particles.inherit = value));
		section.add(radiusSection(entries, configuration.particles.radius, "enchantment.particles.radius").build());

		return section.build();
	}

	private static AbstractConfigListEntry perEnchantmentSection(
			ConfigEntryBuilder entries,
			EnchantmentConfiguration configuration) {
		// the namespace follows the mod id, so a rename cannot leave the keys pointing at the old one
		String namespace = "config." + Limitless.ID + ".";
		// one level field and one switch per registered enchantment, so it starts collapsed
		SubCategoryBuilder section = entries.startSubCategory(Component.translatable(namespace + "enchantments"))
				.setExpanded(false);

		for (EnchantmentEntry entry : configuration.maxLevels) {
			Component name = Component.translatable(namespace + entry.identifier);

			section.add(entries.startIntField(name, entry.maxLevel)
					.setDefaultValue(entry.maxLevel)
					.setMin(1)
					.setMax(Integer.MAX_VALUE)
					.setTooltip(Component.translatable(namespace + "maxLevel"))
					.setSaveConsumer(value -> entry.maxLevel = value)
					.build());

			section.add(entries.startBooleanToggle(name.copy().append(Component.literal(" — ")).append(
							Component.translatable(namespace + "useGlobal")), entry.useGlobalMaxLevel)
					.setDefaultValue(entry.useGlobalMaxLevel)
					.setTooltip(Component.translatable(namespace + "useGlobalMaxLevel"))
					.setSaveConsumer(value -> entry.useGlobalMaxLevel = value)
					.build());
		}

		return section.build();
	}

	private static SubCategoryBuilder radiusSection(ConfigEntryBuilder entries, Radius radius, String key) {
		SubCategoryBuilder section = section(entries, key);

		section.add(field(entries, key + ".xz.min", radius.xz.min, 0, 64, value -> radius.xz.min = value));
		section.add(field(entries, key + ".xz.max", radius.xz.max, 0, 64, value -> radius.xz.max = value));
		section.add(field(entries, key + ".y.min", radius.y.min, -64, 64, value -> radius.y.min = value));
		section.add(field(entries, key + ".y.max", radius.y.max, -64, 64, value -> radius.y.max = value));

		return section;
	}

	private static void anvilCategory(ConfigBuilder builder, ConfigEntryBuilder entries, AnvilConfiguration configuration) {
		ConfigCategory category = builder.getOrCreateCategory(
				Component.translatable("text.autoconfig.limitless-rebuilt.category.anvil"));

		category.addEntry(toggle(entries, "anvil.mergeConflicts", configuration.mergeConflicts,
				value -> configuration.mergeConflicts = value,
				"anvil.mergeConflicts.@Tooltip"));
		category.addEntry(toggle(entries, "anvil.mergeIncompatible", configuration.mergeIncompatible,
				value -> configuration.mergeIncompatible = value,
				"anvil.mergeIncompatible.@Tooltip"));
		BooleanListEntry maxCost = toggle(entries, "anvil.costLimitEnabled", configuration.costLimitEnabled,
				value -> configuration.costLimitEnabled = value,
				"anvil.costLimitEnabled.@Tooltip");
		category.addEntry(maxCost);
		IntegerListEntry levelLimit = field(entries, "anvil.levelLimit", configuration.levelLimit, 0, Integer.MAX_VALUE,
				value -> configuration.levelLimit = value,
				"anvil.levelLimit.@Tooltip");
		levelLimit.setRequirement(Requirement.isFalse(maxCost));
		category.addEntry(levelLimit);
		category.addEntry(field(entries, "anvil.costLimit", configuration.costLimit, 0, Integer.MAX_VALUE,
				value -> configuration.costLimit = value,
				"anvil.costLimit.@Tooltip"));

		SubCategoryBuilder normalization = section(entries, "anvil.normalization");
		normalization.add(toggle(entries, "anvil.normalization.enabled", configuration.normalization.enabled,
				value -> configuration.normalization.enabled = value,
				"anvil.normalization.enabled.@Tooltip"));
		normalization.add(entries.startEnumSelector(
						Component.translatable("text.autoconfig.limitless-rebuilt.option.anvil.normalization.display"),
						CostDisplay.class, configuration.normalization.display)
				.setDefaultValue(configuration.normalization.display)
				.setTooltip(Component.translatable(
						"text.autoconfig.limitless-rebuilt.option.anvil.normalization.display.@Tooltip"))
				.setSaveConsumer(value -> configuration.normalization.display = value)
				.build());

		category.addEntry(normalization.build());
	}

	private static Map<String, Float> parse(List<String> values, Map<String, Float> previous) {
		Map<String, Float> parsed = new LinkedHashMap<>();

		for (String value : values) {
			Float power = previous.get(value);
			parsed.put(value, power == null ? EnchantingBlockConfiguration.DEFAULT_POWER : power);
		}

		return parsed;
	}

	/** A collapsed section hides every option behind a click, so the top level ones start open. */
	private static SubCategoryBuilder section(ConfigEntryBuilder entries, String key) {
		return entries.startSubCategory(Component.translatable("text.autoconfig.limitless-rebuilt.option." + key))
				.setExpanded(true);
	}

	private static me.shedaniel.clothconfig2.gui.entries.BooleanListEntry toggle(
			ConfigEntryBuilder entries,
			String key,
			boolean value,
			java.util.function.Consumer<Boolean> consumer) {
		return toggle(entries, key, value, consumer, null);
	}

	/**
	 * {@code description} names the translation key of a hover note; {@code null} leaves the toggle
	 * bare, which is what every other option here does.
	 */
	private static me.shedaniel.clothconfig2.gui.entries.BooleanListEntry toggle(
			ConfigEntryBuilder entries,
			String key,
			boolean value,
			java.util.function.Consumer<Boolean> consumer,
			String description) {
		BooleanToggleBuilder builder = entries.startBooleanToggle(Component.translatable(translationKey(key)), value)
				.setDefaultValue(value)
				.setSaveConsumer(consumer);

		if (description != null) {
			builder.setTooltip(Component.translatable(translationKey(description)));
		}

		return builder.build();
	}

	private static me.shedaniel.clothconfig2.gui.entries.IntegerListEntry field(
			ConfigEntryBuilder entries,
			String key,
			int value,
			int min,
			int max,
			java.util.function.Consumer<Integer> consumer) {
		return field(entries, key, value, min, max, consumer, null);
	}

	/**
	 * {@code description} names the translation key of a hover note, the same way
	 * {@link #toggle(ConfigEntryBuilder, String, boolean, Consumer, String)} does; {@code null}
	 * leaves the field bare.
	 */
	private static me.shedaniel.clothconfig2.gui.entries.IntegerListEntry field(
			ConfigEntryBuilder entries,
			String key,
			int value,
			int min,
			int max,
			java.util.function.Consumer<Integer> consumer,
			String description) {
		me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder builder = entries.startIntField(
				Component.translatable(translationKey(key)),
				value);

		builder.setDefaultValue(value)
				.setMin(min)
				.setMax(max)
				.setSaveConsumer(consumer);

		if (description != null) {
			builder.setTooltip(Component.translatable(translationKey(description)));
		}

		return builder.build();
	}

	private static String translationKey(String key) {
		return key.startsWith("config.") || key.startsWith("text.")
				? key
				: "text.autoconfig.limitless-rebuilt.option." + key;
	}
}
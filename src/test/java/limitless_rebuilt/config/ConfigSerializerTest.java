package limitless_rebuilt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import limitless_rebuilt.config.common.CostDisplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSerializerTest {
	/** The exact layout AutoConfig + Gson produced for limitless 2.0.0 on 1.19.3. */
	private static final String LEGACY_1_19_3 = """
			{
			  "automaticReloading": true,
			  "enchantment": {
			    "globalMaxLevel": 2147483647,
			    "allowTreasure": true,
			    "revealEnchantments": true,
			    "conflicts": { "generate": true, "merge": true },
			    "normalization": { "enabled": true, "offset": 42 },
			    "reenchanting": {
			      "enabled": true, "removeConflicts": false,
			      "allowEnchantedBooks": true, "allowEquipment": false
			    },
			    "enchantingBlocks": {
			      "maxBlocks": 64,
			      "maxPower": 256,
			      "radius": { "xz": { "min": 1, "max": 16 }, "y": { "min": -16, "max": 4 } },
			      "whitelist": { "examplemod:example_block": 5.0, "minecraft:bookshelf": 3.0 },
			      "blacklist": [ "examplemod:example_block", "minecraft:crafting_table" ]
			    },
			    "particles": {
			      "enabled": true, "inherit": false,
			      "radius": { "xz": { "min": 0, "max": 8 }, "y": { "min": -8, "max": 8 } }
			    },
			    "maxLevels": [
			      { "identifier": "minecraft:sharpness", "maxLevel": 500, "useGlobalMaxLevel": true },
			      { "identifier": "minecraft:lure", "maxLevel": 3, "useGlobalMaxLevel": false }
			    ]
			  },
			  "anvil": {
			    "mergeConflicts": false, "mergeIncompatible": true,
			    "fixedCost": false, "levelLimit": 200,
			    "normalization": { "enabled": false, "display": "APPEND" }
			  }
			}
			""";

	@Test
	@DisplayName("a 1.19.3 configuration file loads unchanged")
	void readsLegacyFile(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, LEGACY_1_19_3, StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertTrue(configuration.automaticReloading);
		assertEquals(Integer.MAX_VALUE, configuration.enchantment.globalMaxLevel);
		assertTrue(configuration.enchantment.allowTreasure);
		assertTrue(configuration.enchantment.revealEnchantments);
		assertTrue(configuration.enchantment.conflicts.generate);
		assertTrue(configuration.enchantment.conflicts.merge);
		assertEquals(42, configuration.enchantment.normalization.offset);
		assertFalse(configuration.enchantment.reenchanting.removeConflicts);
		assertFalse(configuration.enchantment.reenchanting.allowEquipment());
		assertEquals(64, configuration.enchantment.enchantingBlocks.maxBlocks);
		assertEquals(256, configuration.enchantment.enchantingBlocks.maxPower);
		assertEquals(1, configuration.enchantment.enchantingBlocks.radius.xz.min);
		assertEquals(16, configuration.enchantment.enchantingBlocks.radius.xz.max);
		assertEquals(-16, configuration.enchantment.enchantingBlocks.radius.y.min);
		assertEquals(4, configuration.enchantment.enchantingBlocks.radius.y.max);
		assertFalse(configuration.enchantment.particles.inherit);
		assertEquals(500, configuration.enchantment.entry("minecraft:sharpness").maxLevel);
		assertTrue(configuration.enchantment.entry("minecraft:sharpness").useGlobalMaxLevel);
		assertFalse(configuration.anvil.mergeConflicts);
		assertTrue(configuration.anvil.mergeIncompatible);
		// fixedCost is gone; the cap is the new mechanic and defaults to on at 40
		assertTrue(configuration.anvil.costLimitEnabled);
		assertEquals(40, configuration.anvil.costLimit);
		assertEquals(200, configuration.anvil.levelLimit);
		assertEquals(CostDisplay.APPEND, configuration.anvil.normalization.display);
		assertFalse(configuration.anvil.normalization.enabled);
	}

	@Test
	@DisplayName("the examplemod placeholder is dropped from both lists")
	void dropsPlaceholders(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, LEGACY_1_19_3, StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertFalse(configuration.enchantment.enchantingBlocks.whitelist.containsKey("examplemod:example_block"));
		assertFalse(configuration.enchantment.enchantingBlocks.blacklist.contains("examplemod:example_block"));
		assertEquals(3.0F, configuration.enchantment.enchantingBlocks.whitelist.get("minecraft:bookshelf"));
	}

	@Test
	@DisplayName("the shipped per enchantment defaults are restored")
	void restoresDefaultEntries(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{\"enchantment\":{\"maxLevels\":[]}}", StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertEquals(84, configuration.enchantment.entry("minecraft:luck_of_the_sea").maxLevel);
		assertEquals(3, configuration.enchantment.entry("minecraft:lure").maxLevel);
		assertEquals(5, configuration.enchantment.entry("minecraft:quick_charge").maxLevel);
	}

	/**
	 * The tooltip section was added after the first configs had already been written, so most users
	 * have no {@code tooltip} key in their file at all. It shipped with {@code enabled} left unset,
	 * which resolved to false: the lines were silently absent and nothing said why. A file without
	 * the section has to come up enabled.
	 */
	@Test
	@DisplayName("a config without the tooltip section comes up enabled")
	void tooltipDefaultsToEnabled(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{\"enchantment\":{\"maxLevels\":[]}}", StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertTrue(configuration.enchantment.tooltip.enabled);
		assertTrue(configuration.enchantment.tooltip.showDamage);
		assertTrue(configuration.enchantment.tooltip.showKnockback);
		assertTrue(configuration.enchantment.tooltip.showProtection);
	}

	/** An explicit false is the user turning it off and has to survive. */
	@Test
	@DisplayName("the tooltip can still be switched off")
	void tooltipCanBeDisabled(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{\"enchantment\":{\"tooltip\":{\"enabled\":false}}}", StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertFalse(configuration.enchantment.tooltip.enabled);
	}

	@Test
	@DisplayName("an existing entry is not overwritten by the defaults")
	void keepsUserEntries(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(
				file,
				"{\"enchantment\":{\"maxLevels\":[{\"identifier\":\"minecraft:lure\",\"maxLevel\":9}]}}",
				StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertEquals(9, configuration.enchantment.entry("minecraft:lure").maxLevel);
	}

	@Test
	@DisplayName("a missing file is created with the defaults")
	void createsDefaultFile(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);

		Configuration configuration = ConfigSerializer.read(file);

		assertTrue(Files.exists(file));
		assertEquals(512, configuration.enchantment.enchantingBlocks.maxBlocks);
		assertEquals(30, configuration.enchantment.normalization.offset);
		assertEquals(CostDisplay.REPLACE, configuration.anvil.normalization.display);
	}

	@Test
	@DisplayName("missing sections are filled in rather than left null")
	void fillsMissingSections(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{\"anvil\":{}}", StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertNotNull(configuration.enchantment.enchantingBlocks);
		assertNotNull(configuration.enchantment.enchantingBlocks.radius.xz);
		assertNotNull(configuration.enchantment.particles);
		assertNotNull(configuration.anvil.normalization);
		assertEquals(Integer.MAX_VALUE, configuration.anvil.levelLimit);
		assertNull(configuration.enchantment.enchantingBlocks.whitelist.get("nothing"));
	}

	@Test
	@DisplayName("a malformed file is reported and left exactly as it was")
	void survivesMalformedFile(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);

		assertThrows(ConfigReadException.class, () -> ConfigSerializer.read(file));
		assertEquals("{ this is not json", Files.readString(file));
	}

	@Test
	@DisplayName("a file with the wrong shape is not rewritten either")
	void survivesWrongShape(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		String broken = "{\"enchantment\":{\"enchantingBlocks\":{\"whitelist\":[\"a\"]}}}";
		Files.writeString(file, broken, StandardCharsets.UTF_8);

		assertThrows(ConfigReadException.class, () -> ConfigSerializer.read(file));
		assertEquals(broken, Files.readString(file));
	}

	@Test
	@DisplayName("the error names the position and the option that was rejected")
	void errorNamesPositionAndOption(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{\"anvil\":{\"levelLimit\":{\"x\":1}}}", StandardCharsets.UTF_8);

		ConfigReadException failure =
				assertThrows(ConfigReadException.class, () -> ConfigSerializer.read(file));

		assertEquals("anvil.levelLimit", failure.option());
		assertTrue(failure.getMessage().contains("line=1"), failure.getMessage());
		assertTrue(failure.getMessage().contains("column=25"), failure.getMessage());
		assertTrue(failure.getMessage().contains("Expected an int but was BEGIN_OBJECT"), failure.getMessage());
	}

	@Test
	@DisplayName("the error message does not leak Gson's own cause class or its link")
	void errorMessageIsClean(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "{\"anvil\":{\"levelLimit\":{\"x\":1}}}", StandardCharsets.UTF_8);

		ConfigReadException failure =
				assertThrows(ConfigReadException.class, () -> ConfigSerializer.read(file));

		assertFalse(failure.getMessage().contains("java.lang.IllegalStateException"), failure.getMessage());
		assertFalse(failure.getMessage().contains("github.com/google/gson"), failure.getMessage());
	}

	@Test
	@DisplayName("an empty file is filled with the defaults, since there is nothing to lose")
	void fillsEmptyFile(@TempDir Path directory) throws IOException {
		Path file = directory.resolve(Configuration.FILE_NAME);
		Files.writeString(file, "   ", StandardCharsets.UTF_8);

		Configuration configuration = ConfigSerializer.read(file);

		assertEquals(512, configuration.enchantment.enchantingBlocks.maxBlocks);
		assertTrue(Files.size(file) > 0);
	}

	@Test
	@DisplayName("the defaults the fallback hands out are complete")
	void defaultsAreComplete() {
		Configuration configuration = ConfigSerializer.defaults();

		assertEquals(30, configuration.enchantment.normalization.offset);
		assertEquals(CostDisplay.REPLACE, configuration.anvil.normalization.display);
		assertNotNull(configuration.enchantment.enchantingBlocks.whitelist);
	}

	@Test
	@DisplayName("a save and load round trip is lossless")
	void roundTrip(@TempDir Path directory) throws IOException {
		Configuration original = new Configuration();
		original.postLoad();
		original.automaticReloading = true;
		original.enchantment.globalMaxLevel = 12345;
		original.enchantment.reenchanting.allowEquipment = false;
		original.enchantment.enchantingBlocks.radius.y.max = 64;
		original.enchantment.enchantingBlocks.whitelist.put("minecraft:bookshelf", 4.0F);
		original.anvil.normalization.display = CostDisplay.NORMAL;

		Path file = directory.resolve(Configuration.FILE_NAME);
		ConfigSerializer.write(file, original);

		Configuration copy = ConfigSerializer.read(file);

		assertTrue(copy.automaticReloading);
		assertEquals(12345, copy.enchantment.globalMaxLevel);
		assertFalse(copy.enchantment.reenchanting.allowEquipment);
		assertEquals(64, copy.enchantment.enchantingBlocks.radius.y.max);
		assertEquals(4.0F, copy.enchantment.enchantingBlocks.whitelist.get("minecraft:bookshelf"));
		assertEquals(CostDisplay.NORMAL, copy.anvil.normalization.display);
		assertEquals(
				original.enchantment.maxLevels.size(),
				copy.enchantment.maxLevels.size());
	}
}
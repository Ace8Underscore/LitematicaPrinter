package com.cousinware.arete.module.world;

import com.cousinware.arete.client.AreteClient;
import com.cousinware.arete.command.Command;
import com.cousinware.arete.events.event.PacketEvent;
import com.cousinware.arete.events.event.PlayerEatingEvent;
import com.cousinware.arete.managers.Render3DManager;
import com.cousinware.arete.managers.RotationManager;
import com.cousinware.arete.module.Module;
import com.cousinware.arete.utils.client.ColorUtils;
import com.cousinware.arete.utils.client.rotations.RotationRequest;
import com.cousinware.arete.utils.client.rotations.RotationSystem;
import com.cousinware.arete.utils.client.settings.BoolSetting;
import com.cousinware.arete.utils.client.settings.BoolSettingContainer;
import com.cousinware.arete.utils.client.settings.IntSetting;
import com.cousinware.arete.utils.client.settings.ModeSetting;
import com.cousinware.arete.utils.game.InventoryUtils;
import com.cousinware.arete.utils.game.PlayerUtils;
import com.google.common.eventbus.Subscribe;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.http.conn.util.PublicSuffixList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Printer extends Module {

	BoolSetting grimAirPlace = new BoolSetting();
	IntSetting printingRange = new IntSetting();
	IntSetting printingDelay = new IntSetting();
	IntSetting printingRotationDelay = new IntSetting();
	IntSetting printingNormalDelay = new IntSetting();
	public static IntSetting blocksPerTick = new IntSetting();
	ModeSetting swapMode = new ModeSetting();
	BoolSetting pauseOnEat = new BoolSetting();
	BoolSetting pauseOnMove = new BoolSetting();
	ModeSetting rotationMode = new ModeSetting();
	BoolSetting pauseOnRotate = new BoolSetting(); // Packet rotation hidden option
	BoolSetting strictRedstone = new BoolSetting();
	ModeSetting firstSortingMode = new ModeSetting();
	ModeSetting secondSortingMode = new ModeSetting();
	BoolSettingContainer miscSettings = new BoolSettingContainer();
	BoolSetting onlyBelowPrint = new BoolSetting();
	BoolSetting onlyOnGround = new BoolSetting();
	BoolSetting waterToIce = new BoolSetting();


	BoolSettingContainer ignoreBlockVariant = new BoolSettingContainer();
	public final BoolSetting trapdoorsVariant = new BoolSetting();
	public final BoolSetting doorsVariant = new BoolSetting();
	public final BoolSetting fencesVariant = new BoolSetting();
	public final BoolSetting fenceGatesVariant = new BoolSetting();
	public final BoolSetting buttonsVariant = new BoolSetting();
	public final BoolSetting pressurePlatesVariant = new BoolSetting();
	public final BoolSetting signsVariant = new BoolSetting();
	public final BoolSetting hangingSignsVariant = new BoolSetting();
	public final BoolSetting slabsVariant = new BoolSetting();
	public final BoolSetting stairsVariant = new BoolSetting();

	BoolSetting render = new BoolSetting();

	public Printer() {
		super("Printer", Category.World, -1, "Automatically prints open schematics");
		printingRange.setName("PrintingRange").setMin(1).setMax(10).setValue(5).build(this);
		printingDelay.setName("PrintingDelay").setMin(0).setMax(20).setValue(6).setDescription("Delay between printing blocks in ticks").build(this).setModifyAction(() -> {
			int rotationMax = printingDelay.getValue();
			printingRotationDelay.setMax(rotationMax);
			printingNormalDelay.setMax(rotationMax);
			if (printingRotationDelay.getValue() > rotationMax) printingRotationDelay.setValue(rotationMax);
			if (printingNormalDelay.getValue() > rotationMax) printingNormalDelay.setValue(rotationMax);

		});
		printingRotationDelay.setName("RotationDelay").setMin(0).setMax(printingDelay.getValue()).setValue(1).setDescription("Delay after printing a rotation dependent block").build(this);
		printingNormalDelay.setName("NormalPlaceDelay").setMin(0).setMax(printingDelay.getValue()).setValue(0).setDescription("Delay for blocks that cant be placed by airplace").build(this);
		blocksPerTick.setName("BlocksPerTick").setMin(1).setMax(10).setValue(10).setDescription("How many blocks to place per tick").build(this);
		grimAirPlace.setName("Grim3AirPlace").setValue(false).build(this);
		swapMode.setName("Swap").setModes("Silent", "Inventory").setValue("Inventory").setDescription("Silent will not flag but is hotbar only. Inventory is silent but will flag on strict servers and risk desync").build(this);
		pauseOnEat.setName("PauseOnEat").setValue(true).build(this);
		pauseOnMove.setName("PauseOnMove").setValue(false).build(this);
		rotationMode.setName("Rotation").setModes("Packet", "Simulation").setValue("Packet").build(this).setChangeMode(() -> {
			pauseOnRotate.setShown(rotationMode.getValue().equals("Packet"));
		});
		pauseOnRotate.setName("PauseOnRotate").setValue(true).build(this).setShown(rotationMode.getValue().equals("Packet"));
		firstSortingMode.setName("FirstSortMode").setModes("None", "TopDown", "DownTop", "Nearest", "Furthest").setValue("Nearest").build(this);
		secondSortingMode.setName("SecondSortMode").setModes("None", "Nearest", "Furthest", "Fastest").setValue("Nearest").build(this);
		miscSettings.setName("Misc").setValue(false).build(this);
		ignoreBlockVariant.setName("IgnoreBlockVariant").setValue(false).build(this);
		trapdoorsVariant.setName("TrapDoors").setValue(false).build(this, ignoreBlockVariant, "TrapDoorVariant");
		doorsVariant.setName("Doors").setValue(false).build(this, ignoreBlockVariant, "DoorVariant");
		fencesVariant.setName("Fences").setValue(false).build(this, ignoreBlockVariant, "FenceVariant");
		fenceGatesVariant.setName("Fence Gates").setValue(false).build(this, ignoreBlockVariant, "FenceGateVariant");
		buttonsVariant.setName("Buttons").setValue(false).build(this, ignoreBlockVariant, "ButtonVariant");
		pressurePlatesVariant.setName("Pressure Plates").setValue(false).build(this, ignoreBlockVariant, "PressurePlateVariant");
		signsVariant.setName("Signs").setValue(false).build(this, ignoreBlockVariant, "SignVariant");
		hangingSignsVariant.setName("Hanging Signs").setValue(false).build(this, ignoreBlockVariant, "HangingSignVariant");
		slabsVariant.setName("Slabs").setValue(false).build(this, ignoreBlockVariant, "SlabVariant");
		stairsVariant.setName("Stairs").setValue(false).build(this, ignoreBlockVariant, "StairsVariant");


		onlyBelowPrint.setName("OnlyBelow").setValue(false).setDescription("Prints in spots only below the player").build(this, miscSettings, "OnlyBelowPrint");
		onlyOnGround.setName("OnGroundOnly").setValue(false).setDescription("Prints only when the player is onground").build(this, miscSettings, "OnlyOnGround");
		strictRedstone.setName("StrictRedstone").setValue(false).setDescription("When on player is required to be in a valid place pos").build(this, miscSettings, "StrictRedstone");
		waterToIce.setName("WaterToIce").setValue(true).setDescription("Places ice blocks on water source blocks").build(this, miscSettings, "WaterToice");
		render.setName("Render").setValue(true).build(this);
	}

	private int timer;
	private final List<BlockPos> toSort = new ArrayList<>();
	boolean eating = false;
	RotationSystem rotationSystem = new RotationSystem();
	boolean rotationRequested = false;

	@Override
	public void onEnable() {
		timer = 0;
		toSort.clear();

	}

	@Override
	public void onDisable() {
		toSort.clear();
		rotationSystem.forceStop();
	}

	public void onUpdate() {
		if (mc.player == null || mc.level == null) return;
		if (eating) {
			eating = false;
			return;
		}
		if (onlyOnGround.getValue() && !mc.player.onGround()) {
			return;
		}
		if (pauseOnMove.getValue() && PlayerUtils.isMoving()) {
			return;
		}
		if (!isLitematicaLoaded()) {
			Command.sendClientSideMessage(Component.nullToEmpty("Litematica not installed"), false);
			this.disable();
			return;
		}
		WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
		if (worldSchematic == null) {
			Command.sendClientSideMessage(Component.nullToEmpty("No Schematic loaded"), false);
			this.toggle();
			return;
		}


		toSort.clear();

		if (timer >= printingDelay.getValue()) {
			// Collect blocks that need placing
			for (BlockPos pos : getBlocksInRange()) {
				BlockState current = mc.level.getBlockState(pos);
				BlockState required = worldSchematic.getBlockState(pos);

				if (current.canBeReplaced()
						&& !(required.getBlock().equals(Blocks.WATER) && (!required.getFluidState().isSource() || !waterToIce.getValue()))
						&& !required.isAir()
						&& current.getBlock() != required.getBlock()
						&& DataManager.getRenderLayerRange().isPositionWithinRange(pos)
						&& !mc.player.getBoundingBox().intersects(Vec3.atLowerCornerOf(pos), Vec3.atLowerCornerOf(pos).add(1, 1, 1))
						&& required.canSurvive(mc.level, pos)
						&& !required.getBlock().equals(Blocks.PISTON_HEAD) && !required.getBlock().equals(Blocks.MOVING_PISTON)
						&& mc.player.blockPosition().closerThan(pos, printingRange.getValue())) {

					//Special check just for skulls...
					if (required.getBlock() instanceof WallSkullBlock) {
						Direction facing = required.getValue(BlockStateProperties.HORIZONTAL_FACING);
						BlockPos supportPos = pos.relative(facing.getOpposite());
						if (!mc.level.getBlockState(supportPos).isSolidRender()) continue;
					}
					if (onlyBelowPrint.getValue() && pos.getY() > mc.player.blockPosition().getY() - 1) continue;


					toSort.add(new BlockPos(pos));
				}
			}
			timer = 0;

			// Sort
			applySorting();

			if (toSort.isEmpty()) return;
			//get PlaceItem
			Item placeItem = null;
			for (BlockPos pos : toSort) {
				BlockState blockState = worldSchematic.getBlockState(pos);
				Item[] item = getBlockAsItem(blockState);
				//ignore wood typing so any trapdoor in inventory can replace another varient
				if ((swapMode.getValue().equalsIgnoreCase("Inventory") && InventoryUtils.doesInventoryContain(item)) || (swapMode.getValue().equalsIgnoreCase("Silent") && InventoryUtils.doesHotbarContain(item))) {
					placeItem = InventoryUtils.findAvailableItem(item);
					break;
				}
			}
			if (placeItem == null) return;

			// Place
			InventoryUtils.PrinterAirPlaceTask task = new InventoryUtils.PrinterAirPlaceTask(swapMode.getValue().equals("Silent") ? InventoryUtils.SwapType.HotBar : InventoryUtils.SwapType.Inventory, Direction.UP, grimAirPlace.getValue(), placeItem);
			for (BlockPos pos : toSort) {
				if (task.isEnded()) break;
				if (!isSameBlockOrAllowedVariant(placeItem, worldSchematic.getBlockState(pos)))
					continue; //next place block is not same block so lets not add and look to see if we have another block we can add to this batch within the loop
				if (customPlace(worldSchematic, pos, task)) {
					break;
				}
				if (task.placeLocations.size() > blocksPerTick.getValue()) break;
			}
			//messy. if we placed one block then we can do so again next tick unless next block is a batch block IE full block which doesnt require roations etc then we reset timer
			render(task);
			if (rotationRequested) {
				AreteClient.rotationManager.requestRotation(new RotationRequest(100, new RotationManager.Rotation(yRot, xRot), true, true, () -> {
					task.place();
				}));
				rotationRequested = false;
			} else {
				task.place();
			}


		} else {
			timer++;
		}
	}

	public boolean isSameBlockOrAllowedVariant(Item item, BlockState blockState) {
		Block block = blockState.getBlock();

		if (!ignoreBlockVariant.getValue()) return item.equals(getBlockAsItem(blockState)[0]);

		return switch (block) {
			case TrapDoorBlock b when trapdoorsVariant.getValue() -> containsItem(TRAPDOORS, item);
			case DoorBlock b when doorsVariant.getValue() -> containsItem(DOORS, item);
			case FenceBlock b when fencesVariant.getValue() -> containsItem(FENCES, item);
			case FenceGateBlock b when fenceGatesVariant.getValue() -> containsItem(FENCE_GATES, item);
			case ButtonBlock b when buttonsVariant.getValue() -> containsItem(BUTTONS, item);
			case PressurePlateBlock b when pressurePlatesVariant.getValue() -> containsItem(PRESSURE_PLATES, item);
			case SlabBlock b when slabsVariant.getValue() -> containsItem(SLABS, item);
			case StairBlock b when stairsVariant.getValue() -> containsItem(STAIRS, item);
			default -> item.equals(getBlockAsItem(blockState)[0]);
		};
	}

	private boolean containsItem(Item[] items, Item target) {
		for (Item i : items) {
			if (i == target) return true;
		}
		return false;
	}


	//For PauseOnEat Setting
	@Subscribe
	public void playerEatEvent(PlayerEatingEvent event) {
		eating = true;
	}

	//Rendering. Gets called once we find poses to place at
	public void render(InventoryUtils.PrinterAirPlaceTask task) {
		if (render.getValue()) {
			for (BlockPos pos : task.getPlaceLocations()) {
				AreteClient.render3DManager.postRender(Render3DManager.RenderMode.BlockOutline, new AABB(pos), pos.getCenter(), ColorUtils.convertAlpha(AreteClient.getClientColor(), 255), "PrinterOutline" + pos, 250);
				AreteClient.render3DManager.postRender(Render3DManager.RenderMode.SolidBlock, new AABB(pos), pos.getCenter(), ColorUtils.convertAlpha(AreteClient.getClientColor(), 125), "PrinterSolid" + pos, 250);

			}
		}
	}

	private Item[] getBlockAsItem(BlockState blockState) {
		Block block = blockState.getBlock();
		if (block.equals(Blocks.TWISTING_VINES_PLANT)) return new Item[]{Items.TWISTING_VINES};
		if (block.equals(Blocks.WATER)) {
			if (blockState.getFluidState().isSource()) {
				return new Item[]{Items.ICE};
			}
		}
		if (ignoreBlockVariant.getValue()) {
			if (InventoryUtils.doesInventoryContain(block.asItem())) return new Item[]{block.asItem()};
            switch (block) {
                case TrapDoorBlock trapDoorBlock -> {
                    return TRAPDOORS;
                }
                case DoorBlock doorBlock -> {
                    return DOORS;
                }
                case FenceGateBlock fenceGateBlock -> {
                    return FENCE_GATES;
                }
                case FenceBlock fenceBlock -> {
                    return FENCES;
                }
                case ButtonBlock buttonBlock -> {
                    return BUTTONS;
                }
                case PressurePlateBlock pressurePlateBlock -> {
                    return PRESSURE_PLATES;
                }
                case WallHangingSignBlock wallHangingSignBlock -> {
                    return HANGING_SIGNS;
                }
                case SignBlock signBlock -> {
                    return SIGNS;
                }
                case SlabBlock slabBlock -> {
                    return SLABS;
                }
                case StairBlock stairBlock -> {
                    return STAIRS;
                }
                default -> {
                }
            }

		}
		return new Item[]{block.asItem()};
	}

	//custom place loop. If we return true then client will stop batching and do the placements
	private boolean customPlace(WorldSchematic worldSchematic, BlockPos pos, InventoryUtils.PrinterAirPlaceTask task) {
		if (mc.player == null || mc.level == null) return false;
		if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
		if (isNormalPlaceDependent(worldSchematic.getBlockState(pos).getBlock())) { //checks select few rare blocks that force no air place
			task.batch(pos);
			task.forceNoAir(Direction.UP);
			timer = printingDelay.getValue() - printingNormalDelay.getValue();
			return true; //normal place can only be done once a tick so we return true for no more placing
		}
		if (isRotationDependent(worldSchematic.getBlockState(pos))) { //if a block is rotation dependent we go down to 1bps
			if (processBlockState(worldSchematic, pos, task)) {
				task.batch(pos);
				timer = printingDelay.getValue() - printingRotationDelay.getValue();
				return true;
			} else {
				return false; //something failed in processBlockState so we skip and try next block
			}
		}
		return !task.batch(pos);
	}

	//modifies placements and rotations to place a block correctly
	private boolean processBlockState(WorldSchematic worldSchematic, BlockPos pos, InventoryUtils.PrinterAirPlaceTask task) {
		BlockState blockState = worldSchematic.getBlockState(pos);
		Block block = blockState.getBlock();
		Direction placeSide = getDirection(blockState); //face/side block will be place off of
		SlabType slabType = blockState.hasProperty(BlockStateProperties.SLAB_TYPE) ? blockState.getValue(BlockStateProperties.SLAB_TYPE) : null;
		Half halfType = blockState.hasProperty(BlockStateProperties.HALF) ? blockState.getValue(BlockStateProperties.HALF) : null;
		Direction horizontalFacing = blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? blockState.getValue(BlockStateProperties.HORIZONTAL_FACING) : null; //player facing direction upon placement
		Direction facing = blockState.hasProperty(BlockStateProperties.FACING) ? blockState.getValue(BlockStateProperties.FACING) : null; //Another player facing direction upon placement?
		Direction.Axis wantedAxis = blockState.hasProperty(BlockStateProperties.AXIS) ? blockState.getValue(BlockStateProperties.AXIS) : null;
		Direction wantedHopperOrientation = blockState.hasProperty(BlockStateProperties.FACING_HOPPER) ? blockState.getValue(BlockStateProperties.FACING_HOPPER) : null;
		DoorHingeSide doorHinge = blockState.hasProperty(BlockStateProperties.DOOR_HINGE) ? blockState.getValue(BlockStateProperties.DOOR_HINGE) : null;
		Integer rotation16 = blockState.hasProperty(BlockStateProperties.ROTATION_16) ? blockState.getValue(BlockStateProperties.ROTATION_16) : null;
		FrontAndTop frontAndTop = blockState.hasProperty(BlockStateProperties.ORIENTATION) ? blockState.getValue(BlockStateProperties.ORIENTATION) : null;

		//AutoCrafters
		if (frontAndTop != null) {
			Direction front = frontAndTop.front();
			Direction top = frontAndTop.top();

			float yaw;
			float pitch;
			if (front.equals(Direction.UP) || front.equals(Direction.DOWN)) {
				pitch = front.equals(Direction.DOWN) ? -90.0f : 90.0f;
				yaw = front.equals(Direction.UP) ? top.toYRot() : top.getOpposite().toYRot();
			} else {
				yaw = front.getOpposite().toYRot();
				pitch = 0.0f;
			}
			rotate(yaw, pitch);
			return true;
		}

		//Slabs
		if (slabType != null) {
			if (slabType == SlabType.DOUBLE) task.setDirection(Direction.DOWN);
			else if (slabType == SlabType.BOTTOM) task.setDirection(Direction.UP);
			else task.setDirection(Direction.DOWN);
			return true;
		}

		//Controls almost all redstone and more?
		if (placeSide != null) {
			if (isPositionDependent(block) && (placeSide.equals(Direction.UP) || placeSide.equals(Direction.DOWN))) {
				if (isCorrectLocation(placeSide, pos) || !strictRedstone.getValue()) { //if player is in a valid place location we place and continue
					Direction correctPlaceSide = block.equals(Blocks.OBSERVER) ? placeSide.getOpposite() : placeSide;
					task.setDirection(placeSide.getOpposite());
					float yRot = facing != null ? facing.toYRot() : 0;
					float xRot = correctPlaceSide.equals(Direction.UP) ? 90 : -90;
					rotate(yRot, xRot);
					return true;
				} else { //player is not in valid spot so we return false to cancel this place
					return false;
				}
			} else {
				task.setDirection(placeSide.getOpposite());
			}
		}

		//Rotation
		if (horizontalFacing != null) {
			float yRot = horizontalFacing.getOpposite().toYRot();
			if (block instanceof StairBlock || block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof CampfireBlock) {
				yRot = horizontalFacing.toYRot();
				if (doorHinge != null) { // Allows doors to be placed correctly via offset and applys correct rotation
					double scale = doorHinge.equals(DoorHingeSide.RIGHT) ? 0.25 : -0.25; //doors dont really care for rotaion more of interaction point/vec3 from the BlockHitResult
					task.setOffset(horizontalFacing.getClockWise().getUnitVec3().scale(scale));
					yRot += doorHinge.equals(DoorHingeSide.LEFT) ? -20 : 20;
				}
			}
			if (block instanceof AnvilBlock) yRot += 90;
			rotate(yRot, 0);
			Command.sendDebugMessage("Player Horizontal Facing: " + Direction.fromYRot(yRot));
		}

		//Rotations again! for redstone... this is weird
		if (facing != null) {
			if (block.equals(Blocks.OBSERVER)) {
				float yRot = facing.toYRot(); //why are observers weird man. aka opposite any other block that has a facing BlockState
				rotate(yRot, 0);
			} else {
				float yRot = facing.getOpposite().toYRot();
				rotate(yRot, 0);
				//barrel check...
				if (block instanceof BarrelBlock) {
					if (placeSide != null && placeSide.equals(Direction.UP) || placeSide.equals(Direction.DOWN)) {
						rotate(yRot, 90);
					}
				}
			}
		}

		//Heads and banners
		if (rotation16 != null) {
			//Command.sendDebugMessage("Rotation16");
			float yRot = 0;
			float xRot = 0;
			if (block instanceof SkullBlock) {
				yRot = (float) (rotation16 * 22.5);
				task.forceNoAir(Direction.UP);

			} else if (block instanceof BannerBlock) {
				yRot = (float) (rotation16 * 22.5) + 180;
				if (yRot > 180) yRot -= 360; //for some reason banners are opposite of player heads
				task.forceNoAir(Direction.UP);
			}
			rotate(yRot, xRot);
		}

		//heads placed on sides of blocks dont have rotation16
		if (horizontalFacing != null && block instanceof WallSkullBlock) {
			rotate(horizontalFacing.getOpposite().toYRot(), 0);
		}

		//Hoppers
		if (wantedHopperOrientation != null) {
			task.setDirection(wantedHopperOrientation.getOpposite());

		}
		//Trap doors half types will always override placeSide
		if (halfType != null) {
			if (halfType.equals(Half.TOP)) task.setDirection(Direction.DOWN);
			else task.setDirection(Direction.UP);
		}

		return true;
	}


	//returns placement Direction
	private Direction getDirection(BlockState blockState) {
		if (blockState.hasProperty(BlockStateProperties.FACING))
			return blockState.getValue(BlockStateProperties.FACING);
		else if (blockState.hasProperty(BlockStateProperties.AXIS))
			return Direction.fromAxisAndDirection(blockState.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
		else if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
			return Direction.fromAxisAndDirection(blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS), Direction.AxisDirection.POSITIVE);
		else return Direction.UP;
	}

	//Checks to see if a block depends on rotations
	public static boolean isRotationDependent(BlockState state) {
		if (state == null) return false;

		//Checks for crafters
		if (state.hasProperty(BlockStateProperties.ORIENTATION)) return true;

		//Checks for hoppers
		if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) return true;

		// Checks for horizontal directions (Chests, Furnaces, Stairs, Doors)
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return true;

		// Checks for full 6-way directions (Pistons, Droppers, Shulker Boxes)
		if (state.hasProperty(BlockStateProperties.FACING)) return true;

		// Checks for axis alignment (Logs, Pillars, Quartz Pillars)
		if (state.hasProperty(BlockStateProperties.AXIS)) return true;

		// Checks for 16-point sign/banner rotations
		if (state.hasProperty(BlockStateProperties.ROTATION_16)) return true;

		if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) return true;

		return false;
	}

	//Some blocks dont like airplace so we check here
	public static boolean isNormalPlaceDependent(Block block) {
		return block.equals(Blocks.LILY_PAD) ||
				block.equals(Blocks.TWISTING_VINES) || block.equals(Blocks.TWISTING_VINES_PLANT);
	}

  //StrictRedtone
	public static boolean isPositionDependent(Block block) {
		return block instanceof PistonBaseBlock || block instanceof ObserverBlock || block instanceof DispenserBlock;
	}
  
  //StrictRedtone
	public static boolean isCorrectLocation(Direction placeSide, BlockPos pos) {
		Vec3 diff = mc.player.getEyePosition().subtract(Vec3.atCenterOf(pos));
		if (Math.abs(diff.y) < Math.abs(diff.x) || Math.abs(diff.y) < Math.abs(diff.z)) return false;
		return Direction.getApproximateNearest(diff.x, diff.y, diff.z) == placeSide;
	}

	float yRot = 0, xRot = 0;

	private void rotate(float yRot, float xRot) {
		if (rotationMode.getValue().equalsIgnoreCase("Simulation")) {
			rotationRequested = true;
			this.yRot = yRot;
			this.xRot = xRot;
		} else {
			mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yRot, xRot, mc.player.onGround(), mc.player.horizontalCollision));
			if (pauseOnRotate.getValue()) rotationSystem.setRotation(new RotationManager.Rotation(yRot, xRot), true);
		}
	}

	private List<BlockPos> getBlocksInRange() {
		List<BlockPos> blocks = new ArrayList<>();
		int range = printingRange.getValue();
		BlockPos origin = mc.player.blockPosition();

		for (int x = -range; x <= range; x++)
			for (int y = -range; y <= range; y++)
				for (int z = -range; z <= range; z++)
					blocks.add(origin.offset(x, y, z));

		return blocks;
	}


	private void applySorting() {
		String second = secondSortingMode.getValue();
		String first = firstSortingMode.getValue();

		Comparator<BlockPos> secondComp = getComparator(second);
		Comparator<BlockPos> firstComp = getComparator(first);

		if (secondComp != null) toSort.sort(secondComp);
		if (firstComp != null) toSort.sort(firstComp);
	}

	private Comparator<BlockPos> getComparator(String mode) {
		return switch (mode) {
			case "TopDown" -> Comparator.comparingInt(p -> p.getY() * -1);
			case "DownTop" -> Comparator.comparingInt(Vec3i::getY);
			case "Nearest" -> Comparator.comparingDouble(p -> mc.player.blockPosition().distSqr(p));
			case "Furthest" -> Comparator.comparingDouble(p -> -mc.player.blockPosition().distSqr(p));
			case "Fastest" -> {
				WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
				if (worldSchematic == null || toSort.isEmpty()) yield null;

				// Map out how many of each item type are ready to be placed in this instant
				java.util.Map<Item, Long> frequencyMap = new java.util.HashMap<>();
				for (BlockPos pos : toSort) {
					Item item = worldSchematic.getBlockState(pos).getBlock().asItem();
					frequencyMap.put(item, frequencyMap.getOrDefault(item, 0L) + 1);
				}

				// Sort descending by frequency (highest block item count first)
				// Automatically falls back to secondary distance checks if frequencies match
				yield Comparator.<BlockPos>comparingLong(pos -> {
					Item item = worldSchematic.getBlockState(pos).getBlock().asItem();
					return -frequencyMap.getOrDefault(item, 0L);
				});
			}
			default -> null;
		};
	}

	private boolean isLitematicaLoaded() {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			// Get mod metadata
			String modId = mod.getMetadata().getId();

			if (modId.equalsIgnoreCase("litematica")) {
				return true;
			}
		}
		return false;
	}


	public static final Item[] TRAPDOORS = {
			Items.OAK_TRAPDOOR, Items.SPRUCE_TRAPDOOR, Items.BIRCH_TRAPDOOR, Items.JUNGLE_TRAPDOOR, Items.ACACIA_TRAPDOOR,
			Items.DARK_OAK_TRAPDOOR, Items.MANGROVE_TRAPDOOR, Items.CHERRY_TRAPDOOR, Items.PALE_OAK_TRAPDOOR, Items.BAMBOO_TRAPDOOR,
			Items.CRIMSON_TRAPDOOR, Items.WARPED_TRAPDOOR, Items.IRON_TRAPDOOR, Items.COPPER_TRAPDOOR, Items.EXPOSED_COPPER_TRAPDOOR,
			Items.WEATHERED_COPPER_TRAPDOOR, Items.OXIDIZED_COPPER_TRAPDOOR, Items.WAXED_COPPER_TRAPDOOR,
			Items.WAXED_EXPOSED_COPPER_TRAPDOOR, Items.WAXED_WEATHERED_COPPER_TRAPDOOR, Items.WAXED_OXIDIZED_COPPER_TRAPDOOR
	};

	public static final Item[] DOORS = {
			Items.OAK_DOOR, Items.SPRUCE_DOOR, Items.BIRCH_DOOR, Items.JUNGLE_DOOR, Items.ACACIA_DOOR,
			Items.DARK_OAK_DOOR, Items.MANGROVE_DOOR, Items.CHERRY_DOOR, Items.PALE_OAK_DOOR, Items.BAMBOO_DOOR,
			Items.CRIMSON_DOOR, Items.WARPED_DOOR, Items.IRON_DOOR, Items.COPPER_DOOR, Items.EXPOSED_COPPER_DOOR,
			Items.WEATHERED_COPPER_DOOR, Items.OXIDIZED_COPPER_DOOR, Items.WAXED_COPPER_DOOR,
			Items.WAXED_EXPOSED_COPPER_DOOR, Items.WAXED_WEATHERED_COPPER_DOOR, Items.WAXED_OXIDIZED_COPPER_DOOR
	};

	public static final Item[] FENCES = {
			Items.OAK_FENCE, Items.SPRUCE_FENCE, Items.BIRCH_FENCE, Items.JUNGLE_FENCE, Items.ACACIA_FENCE,
			Items.DARK_OAK_FENCE, Items.MANGROVE_FENCE, Items.CHERRY_FENCE, Items.PALE_OAK_FENCE, Items.BAMBOO_FENCE,
			Items.CRIMSON_FENCE, Items.WARPED_FENCE, Items.NETHER_BRICK_FENCE
	};

	public static final Item[] FENCE_GATES = {
			Items.OAK_FENCE_GATE, Items.SPRUCE_FENCE_GATE, Items.BIRCH_FENCE_GATE, Items.JUNGLE_FENCE_GATE, Items.ACACIA_FENCE_GATE,
			Items.DARK_OAK_FENCE_GATE, Items.MANGROVE_FENCE_GATE, Items.CHERRY_FENCE_GATE, Items.PALE_OAK_FENCE_GATE, Items.BAMBOO_FENCE_GATE,
			Items.CRIMSON_FENCE_GATE, Items.WARPED_FENCE_GATE
	};

	public static final Item[] BUTTONS = {
			Items.OAK_BUTTON, Items.SPRUCE_BUTTON, Items.BIRCH_BUTTON, Items.JUNGLE_BUTTON, Items.ACACIA_BUTTON,
			Items.DARK_OAK_BUTTON, Items.MANGROVE_BUTTON, Items.CHERRY_BUTTON, Items.PALE_OAK_BUTTON, Items.BAMBOO_BUTTON,
			Items.CRIMSON_BUTTON, Items.WARPED_BUTTON, Items.STONE_BUTTON, Items.POLISHED_BLACKSTONE_BUTTON
	};

	public static final Item[] PRESSURE_PLATES = {
			Items.OAK_PRESSURE_PLATE, Items.SPRUCE_PRESSURE_PLATE, Items.BIRCH_PRESSURE_PLATE, Items.JUNGLE_PRESSURE_PLATE, Items.ACACIA_PRESSURE_PLATE,
			Items.DARK_OAK_PRESSURE_PLATE, Items.MANGROVE_PRESSURE_PLATE, Items.CHERRY_PRESSURE_PLATE, Items.PALE_OAK_PRESSURE_PLATE, Items.BAMBOO_PRESSURE_PLATE,
			Items.CRIMSON_PRESSURE_PLATE, Items.WARPED_PRESSURE_PLATE, Items.STONE_PRESSURE_PLATE, Items.POLISHED_BLACKSTONE_PRESSURE_PLATE,
			Items.LIGHT_WEIGHTED_PRESSURE_PLATE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE
	};

	public static final Item[] SIGNS = {
			Items.OAK_SIGN, Items.SPRUCE_SIGN, Items.BIRCH_SIGN, Items.JUNGLE_SIGN, Items.ACACIA_SIGN,
			Items.DARK_OAK_SIGN, Items.MANGROVE_SIGN, Items.CHERRY_SIGN, Items.PALE_OAK_SIGN, Items.BAMBOO_SIGN,
			Items.CRIMSON_SIGN, Items.WARPED_SIGN
	};

	public static final Item[] HANGING_SIGNS = {
			Items.OAK_HANGING_SIGN, Items.SPRUCE_HANGING_SIGN, Items.BIRCH_HANGING_SIGN, Items.JUNGLE_HANGING_SIGN, Items.ACACIA_HANGING_SIGN,
			Items.DARK_OAK_HANGING_SIGN, Items.MANGROVE_HANGING_SIGN, Items.CHERRY_HANGING_SIGN, Items.PALE_OAK_HANGING_SIGN, Items.BAMBOO_HANGING_SIGN,
			Items.CRIMSON_HANGING_SIGN, Items.WARPED_HANGING_SIGN
	};

	public static final Item[] SLABS = {
			Items.OAK_SLAB, Items.SPRUCE_SLAB, Items.BIRCH_SLAB, Items.JUNGLE_SLAB, Items.ACACIA_SLAB, Items.DARK_OAK_SLAB,
			Items.MANGROVE_SLAB, Items.CHERRY_SLAB, Items.PALE_OAK_SLAB, Items.BAMBOO_SLAB, Items.CRIMSON_SLAB, Items.WARPED_SLAB,
			Items.STONE_SLAB, Items.SMOOTH_STONE_SLAB, Items.COBBLESTONE_SLAB, Items.MOSSY_COBBLESTONE_SLAB, Items.STONE_BRICK_SLAB,
			Items.MOSSY_STONE_BRICK_SLAB, Items.GRANITE_SLAB, Items.POLISHED_GRANITE_SLAB, Items.DIORITE_SLAB, Items.POLISHED_DIORITE_SLAB,
			Items.ANDESITE_SLAB, Items.POLISHED_ANDESITE_SLAB, Items.BRICK_SLAB, Items.MUD_BRICK_SLAB, Items.MUD_BRICK_SLAB,
			Items.NETHER_BRICK_SLAB, Items.RED_NETHER_BRICK_SLAB, Items.QUARTZ_SLAB, Items.SMOOTH_QUARTZ_SLAB, Items.PURPUR_SLAB,
			Items.END_STONE_BRICK_SLAB, Items.PRISMARINE_SLAB, Items.PRISMARINE_BRICK_SLAB, Items.DARK_PRISMARINE_SLAB,
			Items.SANDSTONE_SLAB, Items.SMOOTH_SANDSTONE_SLAB, Items.RED_SANDSTONE_SLAB,
			Items.CUT_RED_SANDSTONE_SLAB, Items.SMOOTH_RED_SANDSTONE_SLAB, Items.BLACKSTONE_SLAB, Items.POLISHED_BLACKSTONE_SLAB,
			Items.POLISHED_BLACKSTONE_BRICK_SLAB, Items.COBBLED_DEEPSLATE_SLAB, Items.POLISHED_DEEPSLATE_SLAB, Items.DEEPSLATE_BRICK_SLAB,
			Items.DEEPSLATE_TILE_SLAB, Items.TUFF_SLAB, Items.POLISHED_TUFF_SLAB, Items.TUFF_BRICK_SLAB, Items.CUT_COPPER_SLAB,
			Items.EXPOSED_CUT_COPPER_SLAB, Items.WEATHERED_CUT_COPPER_SLAB, Items.OXIDIZED_CUT_COPPER_SLAB, Items.WAXED_CUT_COPPER_SLAB,
			Items.WAXED_EXPOSED_CUT_COPPER_SLAB, Items.WAXED_WEATHERED_CUT_COPPER_SLAB, Items.WAXED_OXIDIZED_CUT_COPPER_SLAB
	};

	public static final Item[] STAIRS = {
			Items.OAK_STAIRS, Items.SPRUCE_STAIRS, Items.BIRCH_STAIRS, Items.JUNGLE_STAIRS, Items.ACACIA_STAIRS, Items.DARK_OAK_STAIRS,
			Items.MANGROVE_STAIRS, Items.CHERRY_STAIRS, Items.PALE_OAK_STAIRS, Items.BAMBOO_STAIRS, Items.CRIMSON_STAIRS, Items.WARPED_STAIRS,
			Items.STONE_STAIRS, Items.COBBLESTONE_STAIRS, Items.MOSSY_COBBLESTONE_STAIRS, Items.STONE_BRICK_STAIRS, Items.MOSSY_STONE_BRICK_STAIRS,
			Items.GRANITE_STAIRS, Items.POLISHED_GRANITE_STAIRS, Items.DIORITE_STAIRS, Items.POLISHED_DIORITE_STAIRS, Items.ANDESITE_STAIRS,
			Items.POLISHED_ANDESITE_STAIRS, Items.BRICK_STAIRS, Items.MUD_BRICK_STAIRS, Items.NETHER_BRICK_STAIRS, Items.RED_NETHER_BRICK_STAIRS,
			Items.QUARTZ_STAIRS, Items.SMOOTH_QUARTZ_STAIRS, Items.PURPUR_STAIRS, Items.END_STONE_BRICK_STAIRS, Items.PRISMARINE_STAIRS,
			Items.PRISMARINE_BRICK_STAIRS, Items.DARK_PRISMARINE_STAIRS, Items.SANDSTONE_STAIRS, Items.SMOOTH_SANDSTONE_STAIRS,
			Items.RED_SANDSTONE_STAIRS, Items.SMOOTH_RED_SANDSTONE_STAIRS, Items.BLACKSTONE_STAIRS, Items.POLISHED_BLACKSTONE_STAIRS,
			Items.POLISHED_BLACKSTONE_BRICK_STAIRS, Items.COBBLED_DEEPSLATE_STAIRS, Items.POLISHED_DEEPSLATE_STAIRS, Items.DEEPSLATE_BRICK_STAIRS,
			Items.DEEPSLATE_TILE_STAIRS, Items.TUFF_STAIRS, Items.POLISHED_TUFF_STAIRS, Items.TUFF_BRICK_STAIRS, Items.CUT_COPPER_STAIRS,
			Items.EXPOSED_CUT_COPPER_STAIRS, Items.WEATHERED_CUT_COPPER_STAIRS, Items.OXIDIZED_CUT_COPPER_STAIRS, Items.WAXED_CUT_COPPER_STAIRS,
			Items.WAXED_EXPOSED_CUT_COPPER_STAIRS, Items.WAXED_WEATHERED_CUT_COPPER_STAIRS, Items.WAXED_OXIDIZED_CUT_COPPER_STAIRS
	};
}

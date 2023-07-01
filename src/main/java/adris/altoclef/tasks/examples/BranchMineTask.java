package adris.altoclef.tasks.examples;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.movement.GetToYTask;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.WorldHelper;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class BranchMineTask extends Task implements ITaskRequiresGrounded {


    //10-element array of relative positions to last branch pos
    //Format: [translation on miningDirection axis, translation on other horizontal access, y translation]
    private static final int[][] BRANCH_RELATIVE_POSITIONS = new int[][]{
            //1 block forward on the Hallway
            {0,0,1},
            {0,0,0},
            //Branch 1
            {0,1,1},
            {0,2,1},
            {0,3,1},
            //Branch 2
            {0,-1,1},
            {0,-2,1},
            {0,-3,1},
            //2 more blocks forward on the Hallway
            {1,0,1},
            {1,0,0},
            {2,0,1},
            {2,0,0}
    };

    //How far the player can be from this task's last branch mine. If we are farther than this distance, we just start a new branch mine instead.
    private static final int MAX_DISTANCE_FROM_BRANCH_BEFORE_ABANDONING = 20;

    //Contains Information regarding the optimal y levels for certain resources
    public static final HashMap<Item, List<Integer>> optimalMiningLevels = new HashMap<>();

    static {

        //Format:
        //Indices 0 and 1 are the min and max range for the ore to be found
        //Index 2 is the MOST optimal y level for the ore
        //Indices 3 and 4 are the min and max for an alternate range for the ore. This range apparently has an even distribution of the ore?
            //Indices 3 and 4 don't always exist.

        optimalMiningLevels.put(Items.RAW_COPPER, List.of(-16,112,48));
        optimalMiningLevels.put(Items.LAPIS_LAZULI, List.of(-32,30,-1,-64,64));
        optimalMiningLevels.put(Items.COAL, List.of(0,190,95));
        optimalMiningLevels.put(Items.RAW_IRON, List.of(-24,54,15,-63,64));
        optimalMiningLevels.put(Items.REDSTONE, List.of(-63,-32,-55,-63,-15));
        optimalMiningLevels.put(Items.RAW_GOLD, List.of(-64,32,-16,-64,-48));
        optimalMiningLevels.put(Items.DIAMOND, List.of(-63,14,-55));
    }



    //Items to Mine. Since this is simply creating a branch mine, this is only used to calculate the optimal y level.
    private ItemTarget[] items;

    //ProgressChecker
    MovementProgressChecker movementProgressChecker = new MovementProgressChecker(3);

    //Y Level to Mine At (Calculated using this.items)
    private Optional<Integer> yLevel;

    //Last Branch Pos (initially null). We treat a branch mine as a set of "modules" consisting of 3 blocks of hallway and 2 branches on either side.
    //This is the position of the last/current module we are working on
    private BlockPos lastBranchPos;

    //The current BlockPos we are trying to mine at.
    private BlockPos minePos;

    //Direction object for the direction of the branch mine
    private Direction miningDirection;

    /**
     *
     * @param items Items to look for in the branch mine. NOTE that this task will not actually mine these ores. It will only choose
     *              the best y value for those ores and branch mine there. This should be used with another task that looks for these ores
     *              and mines them.
     */
    public BranchMineTask(ItemTarget[] items) {
        this.items = items;
        yLevel = Optional.empty();
    }


    @Override
    protected void onStart(AltoClef mod) {

        //Abandon old branch mine if it's too far away
        if(lastBranchPos != null) {
            if(!mod.getPlayer().getBlockPos().isWithinDistance(lastBranchPos, MAX_DISTANCE_FROM_BRANCH_BEFORE_ABANDONING)) {
                lastBranchPos = null;
            }
        }

        //Calculate Y level for branch mine
        if(yLevel.isEmpty()) {
            yLevel = Optional.of(calculateYLevel(this.items));
        }

        //Copper: -16,112,48
        //Lapis: -32,30,-1 or -64,64 evenly
        //Coal: 0,190,95
        //Iron: -24,54,15 or -63,64 evenly
        //Redstone: -63,-32,-55 or -63,-15,evenly
        //Emerald: Just do 0,0,0 bro
        //Gold; -64,32,-16 or -64,-48,evenly
        //Diamond: -63,14,-55

        //If last branch pos isn't null
            //See if we are a certain range from that pos. If so, keep it as it is
            //If too far, set to null
        //If y level isn't calculated, calculate it
    }


    private int calculateYLevel(ItemTarget[] items) {

        //All the items that we should be looking for
        List<Item> goalItems = new ArrayList<>();

        for(ItemTarget itemTarget : items) {
            for(Item item : itemTarget.getMatches()) {
                if(!goalItems.contains(item) && optimalMiningLevels.containsKey(item)) {
                    goalItems.add(item);
                }
            }
        }

        if(goalItems.size() == 0) {
            return 0;
        }

        //Try Best Ranges (this will always use the first range specified in optimalMiningLevels since I think they give the greatest ore yield)
        //Will find the overlap between all these ranges (one range per item/ore type)
        int minRange = Integer.MIN_VALUE;
        int maxRange = Integer.MAX_VALUE;

        for(Item item : goalItems) {
            minRange = Math.max(minRange, optimalMiningLevels.get(item).get(0));
            maxRange = Math.min(maxRange, optimalMiningLevels.get(item).get(1));
        }

        if(minRange <= maxRange) {
            int sum = 0;
            int count = 0;
            //Try to get as close to the most optimal y level for each ore as possible. Average all of them that are in bounds of
            //the overlap range to do that. (That's the current strategy at least)
            for(Item item : goalItems) {
                int optimalYLevelForItem = optimalMiningLevels.get(item).get(2);
                if(minRange <= optimalYLevelForItem && optimalYLevelForItem <= maxRange) {
                    sum += optimalYLevelForItem;
                    count++;
                }
            }

            if(count == 0) {
                return (minRange+maxRange)/2;
            }

            return sum/count;
        }

        //Try Largest Ranges (take the largest range for each item/ore type)
        minRange = Integer.MIN_VALUE;
        maxRange = Integer.MAX_VALUE;

        for(Item item : goalItems) {
            Pair<Integer, Integer> rangeForItem = getLargestRange(item);
            if(rangeForItem == null) {
                continue;
            }
            minRange = Math.max(minRange, rangeForItem.getLeft());
            maxRange = Math.min(maxRange, rangeForItem.getRight());
        }

        if(minRange <= maxRange) {
            return (minRange+maxRange)/2;
        }


        return optimalMiningLevels.get(goalItems.get(0)).get(2);
    }

    private Pair<Integer, Integer> getLargestRange(Item item) {
        if(!optimalMiningLevels.containsKey(item)) {
            return null;
        }
        if(optimalMiningLevels.get(item).size() < 5) {
            return new Pair<>(optimalMiningLevels.get(item).get(0), optimalMiningLevels.get(item).get(1));
        }

        int rangeOne = optimalMiningLevels.get(item).get(1)-optimalMiningLevels.get(item).get(0);
        int rangeTwo = optimalMiningLevels.get(item).get(4)-optimalMiningLevels.get(item).get(3);


        return (rangeOne >= rangeTwo) ? new Pair<>(optimalMiningLevels.get(item).get(0), optimalMiningLevels.get(item).get(1)) :
                new Pair<>(optimalMiningLevels.get(item).get(3), optimalMiningLevels.get(item).get(4));
    }

    @Override
    protected Task onTick(AltoClef mod) {

        //If we are unable to progress forward, change the direction of the strip mine to try to avoid whatever obstacle is in our way.
        if (this.minePos != null && !this.movementProgressChecker.check(mod)) {
            Debug.logMessage("Failed to mine block. Suggesting it may be unreachable.");
            mod.getBlockTracker().requestBlockUnreachable(minePos, 2);
            miningDirection.rotateClockwise(Direction.Axis.Y);
        }

        //If there is no previous branch mine from earlier
        if(lastBranchPos == null) {
            //Calculate the Y Level if not done already (should have been done, so this theoretically shouldn't run)
            if(yLevel.isEmpty()) {
                Debug.logMessage("Y Level for Branch Mine not Calculated. Re-attempting calculation.");
                this.yLevel = Optional.of(calculateYLevel(this.items));
            }
            //If we aren't at the calculated y level, go there.
            if(mod.getPlayer().getBlockPos().getY() != this.yLevel.get()) {
                return new GetToYTask(this.yLevel.get()-1);
            }
            //Start a new branch mine at our location. Choose a direction for the branch mine.
            else {
                Debug.logMessage("Starting Branch Mine");
                this.lastBranchPos = mod.getPlayer().getBlockPos();
                this.miningDirection = chooseDirection(mod);
            }
        }

        Debug.logMessage("Choosing Relative Position");
        //Construct the current branch mine module. Go through each position and break it.
        for(int[] relativePos : BRANCH_RELATIVE_POSITIONS) {
            BlockPos pos = new BlockPos(lastBranchPos);
            pos = pos.add(miningDirection.getVector().multiply(relativePos[0]));
            pos = pos.add(miningDirection.rotateYClockwise().getVector().multiply(relativePos[1]));
            pos = pos.add(0,relativePos[2],0);

            if(!mod.getWorld().getBlockState(pos).isAir() && WorldHelper.canBreak(mod, pos)) {
                this.minePos = pos;
                return new DestroyBlockTask(pos);
            }
        }

        Debug.logMessage("Iterating lastBranchPos");
        //If there was nothing to break in the current branch mine module, iterate the lastBranchPos to go to the next module
        this.lastBranchPos = this.lastBranchPos.add(miningDirection.getVector().multiply(3));


        //Blacklist Logic?
            //If block blacklisted, change direction (rotate 90 degrees)

        //If branch pos is null
            //If not at right y level, go to right y level
            //If at right y level, set last branch pos to current pos
                //Choose Direction (whichever is closest to a wall. Maybe raytrace top and bottom, average, and min)
                    //Probably make into a method

        //Set currentBranchPos to 2 blocks ahead of last branch pos based on the Direction

        //Loop through 10-element array until find one that isn't air. Break that block.

        //If nothing to be broken, return null and wait to iterate next tick (perhaps optimize by iterating until
        //something isn't air? That might lag though?)

        return null;
    }

    /**
     *
     * The distance is calculated by taking the distance from the feet to the closest solid block and the head to the closest solid block.
     * These two values are averaged.
     *
     * @param mod
     * @return The direction that is closest to a "wall."
     */
    private Direction chooseDirection(AltoClef mod) {

        int maxDistance = MinecraftClient.getInstance().options.getViewDistance().getValue() * 16;

        double minAvgDistance = Integer.MAX_VALUE;
        Direction bestDirection = null;

        for(Direction direction : Direction.values()) {
            Vec3d feet = mod.getPlayer().getPos();
            Vec3d head = mod.getPlayer().getPos().add(0,1,0);
            Vec3d endFeet = feet.add(direction.getVector().getX() * maxDistance, 0, direction.getVector().getZ() * maxDistance);
            Vec3d endHead = head.add(direction.getVector().getX() * maxDistance, 0, direction.getVector().getZ() * maxDistance);

            HitResult feetResult = mod.getWorld().raycast(new RaycastContext(feet, endFeet, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mod.getPlayer()));
            HitResult headResult = mod.getWorld().raycast(new RaycastContext(head, endHead, RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, mod.getPlayer()));

            if(feetResult.getType() == HitResult.Type.MISS || headResult.getType() == HitResult.Type.MISS) {
                continue;
            }

            double avgDistance = (feetResult.squaredDistanceTo(mod.getPlayer()) + headResult.squaredDistanceTo(mod.getPlayer()))/2;

            if(avgDistance <= minAvgDistance) {
                minAvgDistance = avgDistance;
                bestDirection = direction;
            }
        }

        return (bestDirection != null) ? bestDirection : Direction.EAST;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqual(Task other) {
        return false;
    }

    @Override
    protected String toDebugString() {
        return null;
    }
}

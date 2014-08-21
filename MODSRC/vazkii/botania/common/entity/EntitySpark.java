/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Botania Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 * 
 * Botania is Open Source and distributed under a
 * Creative Commons Attribution-NonCommercial-ShareAlike 3.0 License
 * (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 * 
 * File Created @ [Aug 21, 2014, 5:43:44 PM (GMT)]
 */
package vazkii.botania.common.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.spark.ISparkAttachable;
import vazkii.botania.api.mana.spark.ISparkEntity;
import vazkii.botania.api.mana.spark.SparkHelper;
import vazkii.botania.common.Botania;
import vazkii.botania.common.core.helper.Vector3;
import vazkii.botania.common.item.ModItems;

public class EntitySpark extends Entity implements ISparkEntity {

	private static final int TRANSFER_RATE = 1000;
	private static final String TAG_UPGRADE = "upgrade";

	public EntitySpark(World world) {
		super(world);
	}

	@Override
	protected void entityInit() {
		setSize(0.5F, 0.5F);
		dataWatcher.addObject(28, 0);
		dataWatcher.addObject(29, "");

		dataWatcher.setObjectWatched(28);
		dataWatcher.setObjectWatched(29);
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		ISparkAttachable tile = getAttachedTile();
		if(tile == null) {
			setDead();
			return;
		}

		int upgrade = getUpgrade();
		if(upgrade != 0) {
			List<ISparkEntity> sparks = SparkHelper.getSparksAround(worldObj, posX, posY, posZ);
			switch(upgrade) {
			case 1 : { // Dispersive
				List<EntityPlayer> players = SparkHelper.getEntitiesAround(EntityPlayer.class, worldObj, posX, posY, posZ);
				break;
			}
			case 2 : { // Dominant
				for(ISparkEntity spark : sparks) {
					int upgrade_ = spark.getUpgrade();
					if(upgrade == 0 || upgrade == 3) 
						spark.registerTransfer(this);
				}
				break;
			}
			case 3 : { // Recessive
				for(ISparkEntity spark : sparks) {
					int upgrade_ = spark.getUpgrade();
					if(upgrade != 4) 
						registerTransfer(spark);
				}
				break;
			}
			}
		}

		Collection<ISparkEntity> sparks = getTransfers();
		if(!sparks.isEmpty()) {
			int manaTotal = Math.min(TRANSFER_RATE * sparks.size(), tile.getCurrentMana());
			int manaForEach = manaTotal / sparks.size();
			if(manaForEach > sparks.size())
				for(ISparkEntity spark : sparks) {
					ISparkAttachable attached = spark.getAttachedTile();
					attached.recieveMana(manaForEach);
					
					particlesTowards((Entity) spark);
				}
			tile.recieveMana(-manaForEach);
		}
	}

	void particlesTowards(Entity e) {
		Vector3 thisVec = Vector3.fromEntityCenter(this).add(0, 0, 0);
		Vector3 receiverVec = Vector3.fromEntityCenter(e).add(0, 0, 0);

		double rc = 0.45;
		thisVec.add((Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc);
		receiverVec.add((Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc, (Math.random() - 0.5) * rc);

		Vector3 motion = receiverVec.copy().sub(thisVec);
		motion.multiply(0.04125F);
		float r = 0.3F + 0.6F * (float) Math.random();
		float g = 0.3F + 0.6F * (float) Math.random();
		float b = 0.3F + 0.6F * (float) Math.random();

		Botania.proxy.wispFX(worldObj, thisVec.x, thisVec.y, thisVec.z, r, g, b, 0.25F, (float) motion.x, (float) motion.y, (float) motion.z);
	}

	@Override
	public boolean canBeCollidedWith() {
		return true;
	}

	@Override
	public boolean interactFirst(EntityPlayer player) {
		ItemStack stack = player.getCurrentEquippedItem(); 
		if(stack != null) {
			int upgrade = getUpgrade();
			if(stack.getItem() == ModItems.twigWand) {
				if(upgrade > 0) { 
					if(!worldObj.isRemote)
						entityDropItem(new ItemStack(ModItems.sparkUpgrade, 1, upgrade + 1), 0F);
					setUpgrade(0);
				} else setDead();
				return true;
			} else if(stack.getItem() == ModItems.sparkUpgrade && upgrade == 0) {
				int newUpgrade = stack.getItemDamage() + 1;
				setUpgrade(newUpgrade);
				stack.stackSize--;
				return true;
			}
		}

		return false;
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound cmp) {
		setUpgrade(cmp.getInteger(TAG_UPGRADE));
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound cmp) {
		cmp.setInteger(TAG_UPGRADE, getUpgrade());
	}

	@Override
	public void setDead() {
		super.setDead();
		if(!worldObj.isRemote) {
			int upgrade = getUpgrade();
			entityDropItem(new ItemStack(ModItems.spark), 0F);
			if(upgrade > 0)
				entityDropItem(new ItemStack(ModItems.sparkUpgrade, 1, upgrade + 1), 0F);
		}
	}

	@Override
	public ISparkAttachable getAttachedTile() {
		int x = (int) MathHelper.floor_double(posX);
		int y = (int) MathHelper.floor_double(posY) - 1;
		int z = (int) MathHelper.floor_double(posZ);
		TileEntity tile = worldObj.getTileEntity(x, y, z);
		if(tile != null && tile instanceof ISparkAttachable)
			return (ISparkAttachable) tile;

		return null;
	}


	@Override
	public Collection<ISparkEntity> getTransfers() {
		Collection<ISparkEntity> entities = new ArrayList();
		String transfers = dataWatcher.getWatchableObjectString(29);
		String[] tokens = transfers.split(";");
		List<String> removals = new ArrayList();

		for(String s : tokens) {
			if(s.isEmpty())
				continue;

			int id = Integer.parseInt(s);
			boolean added = false;
			Entity e = worldObj.getEntityByID(id);
			if(e != null && e instanceof ISparkEntity) {
				ISparkEntity spark = (ISparkEntity) e;
				if(!spark.areIncomingTransfersDone() && spark.getAttachedTile() != null && !spark.getAttachedTile().isFull()) {
					entities.add((ISparkEntity) e);
					added = true;
				}
			}

			if(!added)
				removals.add(s);
		}

		if(!removals.isEmpty()) {
			String newTranfers = "";
			for(String s : tokens)
				if(!removals.contains(s))
					newTranfers = newTranfers + (newTranfers.isEmpty() ? "" : ";") + s;
			dataWatcher.updateObject(29, newTranfers);

			removals.clear();
		}

		return entities;
	}

	@Override
	public void registerTransfer(ISparkEntity entity) {
		if(worldObj.isRemote || getTransfers().contains(entity))
			return;

		String transfers = dataWatcher.getWatchableObjectString(29);
		dataWatcher.updateObject(29, transfers + (transfers.isEmpty() ? "" : ";") + ((Entity) entity).getEntityId()); 
	}

	@Override
	public int getUpgrade() {
		return dataWatcher.getWatchableObjectInt(28);
	}

	@Override
	public void setUpgrade(int upgrade) {
		dataWatcher.updateObject(28, upgrade);
	}

	@Override
	public boolean areIncomingTransfersDone() {
		ISparkAttachable tile = getAttachedTile();
		return tile != null && tile.areIncomingTranfersDone();
	}

}

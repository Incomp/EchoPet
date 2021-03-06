/*
 * This file is part of EchoPet.
 *
 * EchoPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EchoPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with EchoPet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dsh105.echopet.api.pet;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitRunnable;

import com.captainbern.minecraft.protocol.PacketType;
import com.captainbern.minecraft.wrapper.WrappedPacket;
import com.dsh105.commodus.StringUtil;
import com.dsh105.commodus.particle.Particle;
import com.dsh105.echopet.compat.api.entity.*;
import com.dsh105.echopet.compat.api.event.PetTeleportEvent;
import com.dsh105.echopet.compat.api.plugin.EchoPet;
import com.dsh105.echopet.compat.api.plugin.uuid.UUIDMigration;
import com.dsh105.echopet.compat.api.util.Lang;
import com.dsh105.echopet.compat.api.util.PetNames;
import com.dsh105.echopet.compat.api.util.PlayerUtil;
import com.dsh105.echopet.compat.api.util.StringSimplifier;

public abstract class Pet implements IPet{

    private IEntityPet entityPet;
    private PetType petType;

    private Object ownerIdentification;
    private Pet rider;
    private String name;
    private ArrayList<PetData> petData = new ArrayList<PetData>();
	private InventoryView dataMenu;

    private boolean isRider = false;

    public boolean ownerIsMounting = false;
    private boolean ownerRiding = false;
    private boolean isHat = false;

    public Pet(Player owner) {
        if (owner != null) {
            this.ownerIdentification = UUIDMigration.getIdentificationFor(owner);
            this.setPetType();
            this.setPetName(this.getPetType().getDefaultName(this.getNameOfOwner()));
			this.spawnPet(owner);
        }
    }

    protected void setPetType() {
        EntityPetType entityPetType = this.getClass().getAnnotation(EntityPetType.class);
        if (entityPetType != null) {
            this.petType = entityPetType.petType();
        }
    }

	public IEntityPet spawnPet(Player owner){
		if(entityPet != null) return entityPet;
		if(owner != null){
			this.entityPet = EchoPet.getPlugin().getSpawnUtil().spawn(this, owner);
			if(this.entityPet != null){
				this.applyPetName();
				this.teleportToOwner();
				for(PetData pd : getPetData())// hrrm..
					EchoPet.getManager().setData(this, pd, true);
			}
		}else{
			EchoPet.getManager().saveFileData("autosave", this);
			EchoPet.getSqlManager().saveToDatabase(this, false);
			EchoPet.getManager().removePet(this, false);
			removePet(false, false);
		}
		return entityPet;
	}

    @Override
    public IEntityPet getEntityPet() {
        return this.entityPet;
    }

    @Override
    public Creature getCraftPet() {
        return this.getEntityPet().getBukkitEntity();
    }

    @Override
    public Location getLocation() {
        return this.getCraftPet().getLocation();
    }

    @Override
    public Player getOwner() {
        if (this.ownerIdentification == null) {
            return null;
        }
        if (this.ownerIdentification instanceof UUID) {
            return Bukkit.getPlayer((UUID) ownerIdentification);
        } else {
            return Bukkit.getPlayerExact((String) this.ownerIdentification);
        }
    }

    @Override
    public String getNameOfOwner() {
        if (this.ownerIdentification instanceof String) {
            return (String) this.ownerIdentification;
        } else {
            return this.getOwner() == null ? "" : this.getOwner().getName();
        }
    }

    @Override
    public UUID getOwnerUUID() {
        if (this.ownerIdentification instanceof UUID) {
            return (UUID) this.ownerIdentification;
        } else {
            return this.getOwner() == null ? null : this.getOwner().getUniqueId();
        }
    }

    @Override
    public Object getOwnerIdentification() {
        return ownerIdentification;
    }

    @Override
    public PetType getPetType() {
        return this.petType;
    }

    @Override
    public boolean isRider() {
        return this.isRider;
    }

    protected void setRider() {
        this.isRider = true;
    }

    @Override
    public boolean isOwnerInMountingProcess() {
        return ownerIsMounting;
    }

    @Override
    public Pet getRider() {
        return this.rider;
    }

    @Override
    public String getPetName() {
        return name;
    }

    @Override
    public String getPetNameWithoutColours() {
        return ChatColor.stripColor(this.getPetName());
    }
    
    @Override
    public String serialisePetName() {
        return getPetName().replace(ChatColor.COLOR_CHAR, '&');
    }

    @Override
    public boolean setPetName(String name) {
        return this.setPetName(name, true);
    }

    @Override
    public boolean setPetName(String name, boolean sendFailMessage) {
        if (PetNames.allow(name, this)) {
            this.name = ChatColor.translateAlternateColorCodes('&', name);
            if (EchoPet.getPlugin().getMainConfig().getBoolean("stripDiacriticsFromNames", true)) {
                this.name = StringSimplifier.stripDiacritics(this.name);
            }
            if (this.name == null || this.name.equalsIgnoreCase("")) {
                this.name = this.petType.getDefaultName(this.getNameOfOwner());
            }
            this.applyPetName();
            return true;
        } else {
            if (sendFailMessage) {
                if (this.getOwner() != null) {
                    Lang.sendTo(this.getOwner(), Lang.NAME_NOT_ALLOWED.toString().replace("%name%", name));
                }
            }
            return false;
        }
    }

    private void applyPetName() {
        if (this.getEntityPet() != null && this.getCraftPet() != null) {
            this.getCraftPet().setCustomName(this.name);
            this.getCraftPet().setCustomNameVisible(EchoPet.getConfig().getBoolean("pets." + this.getPetType().toString().toLowerCase().replace("_", " ") + ".tagVisible", true));
        }
    }

    @Override
	public ArrayList<PetData> getPetData(){
        return this.petData;
    }

	@Override
	public void removeRider(boolean makeSound, boolean makeParticles){
        if (rider != null) {
			rider.removePet(makeSound, makeParticles);
			rider = null;
			EchoPet.getPlugin().getSpawnUtil().removePassenger(getCraftPet());
        }
    }

    @Override
	public void removePet(boolean makeSound, boolean makeParticles){
		if(getEntityPet() != null && this.getCraftPet() != null && makeParticles){
            Particle.CLOUD.builder().at(getLocation()).show();
            Particle.LAVA_SPARK.builder().at(getLocation()).show();
        }
		removeRider(makeSound, makeParticles);
        if (this.getEntityPet() != null) {
            this.getEntityPet().remove(makeSound);
			this.entityPet = null;
        }
    }

    @Override
    public boolean teleportToOwner() {
        if (this.getOwner() == null || this.getOwner().getLocation() == null) {
			EchoPet.getManager().saveFileData("autosave", this);
			EchoPet.getSqlManager().saveToDatabase(this, false);
			EchoPet.getManager().removePet(this, false);
			removePet(false, true);
            return false;
        }
		if(this.getEntityPet() == null || this.getEntityPet().isDead()){
			EchoPet.getManager().saveFileData("autosave", this);
			EchoPet.getSqlManager().saveToDatabase(this, false);
			removePet(false, false);
			return false;
		}
		Pet rider = getRider();
		removeRider(false, false);
		boolean tele = teleport(this.getOwner().getLocation());
		if(tele && rider != null){
			this.rider = rider;
			this.rider.spawnPet(getOwner());
			EchoPet.getPlugin().getSpawnUtil().setPassenger(0, getCraftPet(), rider.getCraftPet());
			EchoPet.getSqlManager().saveToDatabase(rider, true);
		}
		return tele;
    }

    @Override
    public boolean teleport(Location to) {
		if(this.getOwner() == null || this.getOwner().getLocation() == null){
			EchoPet.getManager().saveFileData("autosave", this);
			EchoPet.getSqlManager().saveToDatabase(this, false);
			EchoPet.getManager().removePet(this, false);
			this.removePet(false, true);
			return false;
		}
        if (this.getEntityPet() == null || this.getEntityPet().isDead()) {
            EchoPet.getManager().saveFileData("autosave", this);
            EchoPet.getSqlManager().saveToDatabase(this, false);
			removePet(false, false);
			spawnPet(getOwner());
            return false;
        }
        PetTeleportEvent teleportEvent = new PetTeleportEvent(this, this.getLocation(), to);
        EchoPet.getPlugin().getServer().getPluginManager().callEvent(teleportEvent);
        if (teleportEvent.isCancelled()) {
            return false;
        }
        Location l = teleportEvent.getTo();
        if (l.getWorld() == this.getLocation().getWorld()) {
            if (this.getRider() != null) {
                this.getRider().getCraftPet().eject();
                this.getRider().getCraftPet().teleport(l);
            }
            this.getCraftPet().teleport(l);
            if (this.getRider() != null) {
                this.getCraftPet().setPassenger(this.getRider().getCraftPet());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isOwnerRiding() {
        return this.ownerRiding;
    }

    @Override
    public boolean isHat() {
        return this.isHat;
    }

	@Override
    public void ownerRidePet(boolean flag) {
        if (this.ownerRiding == flag) {
            return;
        }

        this.ownerIsMounting = true;

        if (this.isHat) {
            this.setAsHat(false);
        }

        if (!flag) {
			getCraftPet().eject();
            if (this.getEntityPet() instanceof IEntityNoClipPet) {
                ((IEntityNoClipPet) this.getEntityPet()).noClip(true);
            }
            ownerIsMounting = false;
        } else {
			if(getRider() != null){
				getRider().removePet(false, true);
            }
            new BukkitRunnable() {
                @Override
                public void run() {
					getCraftPet().setPassenger(getOwner());// Can't do nms method here due to requiring a 2nd update which I don't feel lik doing.
                    ownerIsMounting = false;
                    if (getEntityPet() instanceof IEntityNoClipPet) {
                        ((IEntityNoClipPet) getEntityPet()).noClip(false);
                    }
                }
            }.runTaskLater(EchoPet.getPlugin(), 5L);
        }
        this.teleportToOwner();
        this.getEntityPet().resizeBoundingBox(flag);
        this.ownerRiding = flag;
        Particle.PORTAL.builder().at(getLocation()).show();
        Location l = this.getLocation().clone();
        l.setY(l.getY() - 1D);
        Particle.BLOCK_DUST.builder().ofBlockType(l.getBlock().getType()).at(getLocation()).show();
    }

	@Override
	public void setAsHat(boolean flag){
		if(isHat == flag){
            return;
        }
		if(ownerRiding){
			ownerRidePet(false);
        }
        this.teleportToOwner();

		// The fact forcefully setting the passenger requires an update still baffles me.
		// Why do I still develop for this game..
		WrappedPacket packet = new WrappedPacket(PacketType.Play.Server.MOUNT);
		packet.getIntegers().write(0, getOwner().getEntityId());
        if (!flag) {
			EchoPet.getPlugin().getSpawnUtil().removePassenger(getOwner());// This nms method isn't needed here.. but I've lost all hope in mojang so its a safeguard.
			// getOwner().eject();
			packet.getIntegerArrays().write(0, new int[1]);
        } else {
			EchoPet.getPlugin().getSpawnUtil().setPassenger(0, getOwner(), getCraftPet());
			// getOwner().setPassenger(getCraftPet());
			int[] passengers = {getEntityPet().getBukkitEntity().getEntityId()};
			packet.getIntegerArrays().write(0, passengers);
        }
		PlayerUtil.sendPacket(getOwner(), packet.getHandle());
        this.getEntityPet().resizeBoundingBox(flag);
        this.isHat = flag;
        Particle.PORTAL.builder().at(getLocation()).show();
        Location l = this.getLocation().clone();
        l.setY(l.getY() - 1D);
        Particle.PORTAL.builder().at(getLocation()).show();
    }

    @Override
    public Pet createRider(final PetType pt, boolean sendFailMessage) {
        if (pt == PetType.HUMAN) {
            if (sendFailMessage) {
                Lang.sendTo(this.getOwner(), Lang.RIDERS_DISABLED.toString().replace("%type%", StringUtil.capitalise(this.getPetType().toString())));
            }
            return null;
        }
        if (!EchoPet.getOptions().allowRidersFor(this.getPetType())) {
            if (sendFailMessage) {
                Lang.sendTo(this.getOwner(), Lang.RIDERS_DISABLED.toString().replace("%type%", StringUtil.capitalise(this.getPetType().toString())));
            }
            return null;
        }
        if (this.isOwnerRiding()) {
            this.ownerRidePet(false);
        }
        if (this.rider != null) {
			this.removeRider(true, true);
        }
        IPet newRider = pt.getNewPetInstance(this.getOwner());
        if (newRider == null) {
            if (sendFailMessage) {
                Lang.sendTo(getOwner(), Lang.PET_TYPE_NOT_COMPATIBLE.toString().replace("%type%", StringUtil.capitalise(getPetType().toString())));
            }
            return null;
        }
		newRider.spawnPet(getOwner());
        this.rider = (Pet) newRider;
        this.rider.setRider();
		if(getEntityPet() != null && getCraftPet() != null){
			EchoPet.getPlugin().getSpawnUtil().setPassenger(0, getCraftPet(), newRider.getCraftPet());
		}
		EchoPet.getSqlManager().saveToDatabase(rider, true);

        return this.rider;
    }

	public InventoryView getInventoryView(){
		return dataMenu;
	}

	public void setInventoryView(InventoryView dataMenu){
		this.dataMenu = dataMenu;
	}
}

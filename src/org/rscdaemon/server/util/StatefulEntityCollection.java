/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.rscdaemon.server.model.Entity;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class StatefulEntityCollection<T extends Entity> {
    private Set<T> newEntities = new HashSet<T>();
    private Set<T> knownEntities = new HashSet<T>();
    private Set<T> entitiesToRemove = new HashSet<T>();

    public void add(T entity) {
        this.newEntities.add(entity);
    }

    public void add(Collection<T> entities) {
        this.newEntities.addAll(entities);
    }

    public boolean contains(T entity) {
        return this.newEntities.contains(entity) || this.knownEntities.contains(entity);
    }

    public void remove(T entity) {
        this.entitiesToRemove.add(entity);
    }

    public boolean isRemoving(T entity) {
        return this.entitiesToRemove.contains(entity);
    }

    /*
     * An entity can be added and removed inside the same tick -- something
     * spawns and is taken again before the client was ever told about it. That
     * one is in newEntities and never reached knownEntities, so removeAll below
     * does not touch it, and without the second line addAll would then promote
     * a dead entity into knownEntities and clear the removal that was meant to
     * cancel it.
     *
     * Dropping it from newEntities as well makes the pair cancel out, which is
     * what "added and removed before anyone heard about it" should mean.
     */
    public void update() {
        this.knownEntities.removeAll(this.entitiesToRemove);
        this.newEntities.removeAll(this.entitiesToRemove);
        this.knownEntities.addAll(this.newEntities);
        this.newEntities.clear();
        this.entitiesToRemove.clear();
    }

    /*
     * Drops everything without queueing removals. Only correct when the
     * client's copy has already been discarded by other means -- a region
     * purge (packet 115) on teleport -- because nothing here will ever tell
     * it to. Queueing removals instead would be wrong in that case: the
     * removal bytes address tiles relative to the player, one signed byte
     * each, and a teleport has just moved the player too far for the
     * offsets to fit.
     */
    public void forgetAll() {
        this.knownEntities.clear();
        this.newEntities.clear();
        this.entitiesToRemove.clear();
    }

    public boolean changed() {
        return !this.entitiesToRemove.isEmpty() || !this.newEntities.isEmpty();
    }

    public Collection<T> getRemovingEntities() {
        return this.entitiesToRemove;
    }

    public Collection<T> getNewEntities() {
        return this.newEntities;
    }

    public Collection<T> getKnownEntities() {
        return this.knownEntities;
    }

    public Collection<T> getAllEntities() {
        HashSet<T> temp = new HashSet<T>();
        temp.addAll(this.newEntities);
        temp.addAll(this.knownEntities);
        return temp;
    }
}


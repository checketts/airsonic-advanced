package org.airsonic.player.domain.entity;

import org.airsonic.player.domain.Album;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "starred_album", uniqueConstraints = @UniqueConstraint(columnNames = { "album_id", "username" }))
public class StarredAlbum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "album_id", nullable = false)
    private Integer albumId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "created", nullable = false)
    private Instant created;

    @ManyToOne
    @JoinColumn(name = "album_id", insertable = false, updatable = false)
    private Album album;

    public StarredAlbum() {
    }

    public StarredAlbum(Integer albumId, String username, Instant created) {
        this.albumId = albumId;
        this.username = username;
        this.created = created;
    }

    public StarredAlbum(Album album, String username, Instant created) {
        this.albumId = album.getId();
        this.username = username;
        this.created = created;
    }

    public Integer getId() {
        return id;
    }

    public Integer getAlbumId() {
        return albumId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getCreated() {
        return created;
    }

    public Album getAlbum() {
        return album;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setAlbumId(Integer albumId) {
        this.albumId = albumId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public void setAlbum(Album album) {
        this.albumId = album.getId();
        this.album = album;
    }

}

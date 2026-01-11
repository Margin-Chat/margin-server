package org.margin.server.social.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceChannelDTO {
    private Long id;
    private String name;
    private String description;
    private Long spaceId;

    public SpaceChannelDTO(SpaceChannel channel) {
        this.id = channel.getId();
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.spaceId = channel.getSpaceId();
    }
}

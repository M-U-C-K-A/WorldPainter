---
title: Trees and materials
description: The custom tree pack and the ready made material mixes included with WorldPainter Modernised.
---

# Trees and materials

## Tree pack

WorldPainter Modernised includes a pack of custom trees, generated in eight shapes and sizes each so that no two neighbours look the same:

| Trees | |
|---|---|
| Tall spruces | Long bare trunks with sparse tiers of short branches |
| Firs | Dense cones |
| Rainforest giants | Tall, thick trunks with buttress roots and wide, flat canopies |
| Big oaks | A few thick branches, each ending in a rounded clump of leaves |
| Birches | Slender, with a narrow crown |
| Cherry trees | A short trunk forking into spreading branches with broad pink crowns |
| Palm trees | Leaning, curving trunks with drooping fronds |
| Dead trees | Bare trunks with crooked branches |
| Bushes | Low rounded bushes of oak or azalea leaves |
| Acacias | Slanting limbs carrying flat umbrellas of leaves |
| Willows | A broad crown with curtains of leaves hanging from its rim |
| Azalea bushes | Low round bushes with flowering patches |
| Pale oaks | Thick trunks under broad, grey-green crowns |
| Giant redwoods | Very tall, thick trunks with a narrow crown in their upper half |

Paint them straight away from the **Packs** panel (see below), or use them with the **Paint flora** node of the [node graph](node-graph.html), where they are listed with *(tree pack)*. Each kind of tree becomes a custom object layer, which you can edit like any other.

## The Packs panel

The **Packs** panel, next to Terrain, Layers and Biomes, is a library of everything above, ready to paint without any setting up. Click an item: it is added to the world (as a layer in the *Packs* palette, or as a custom terrain type) and selected for painting; click it again later to select it again. A search field filters the items by name.

| Section | Contains |
|---|---|
| Trees | The fourteen kinds of the tree pack |
| Flowers | Wildflower meadow, tulip field, sunflower field, roses and lilacs, spring flowers, pink petals, swamp flowers, flowers |
| Plants & ground cover | Grass and flowers, tall grass, ferns, forest floor, bushes, dry grass, desert plants, jungle undergrowth, pale garden, mushrooms |
| Water plants | Lily pads, seagrass and kelp, coral garden, sugar cane |
| Crops | Farm crops (on farmland), pumpkins and melons |
| Grass, dirt, stone, sand, cliff, snow and volcanic mixes | The material mixes below |

Plant layers are painted with an intensity: the higher it is, the denser the plants.

## Material mixes

Natural ground is rarely made of one block. The material mixes are ready made mixes of blocks, in patches, noise or layers:

| Grass | Dirt and soil | Stone | Sand and shore | Cliffs and strata | Snow, ice and volcanic |
|---|---|---|---|---|---|
| Meadow | Mixed dirt | Rocky ground | Beach mix | Red cliff strata | Snowy rock |
| Forest floor | Coarse dirt | Limestone | Sand and gravel | Grey cliff strata | Glacier |
| Mossy ground | Taiga floor | Mossy rock | Riverbed | Sandstone strata | Volcanic rock |
| Grass and dirt | Trodden path | Alpine scree | Coral reef | White chalk strata | |
| Grass and stone | Swamp mud | Cobblestone mix | Desert mix | Badlands strata | |
| Lush grass | Mud and clay | Andesite mix | Red sand mix | | |
| Dry grass | | Granite mix | | | |
| | | Deepslate mix | | | |
| | | Gravel mix | | | |

They are listed in the Terrain panel and in the Packs panel. Hover over one to see its blocks and their shares; click it to add it as a custom terrain type and paint with it.

The cliff strata are horizontal bands of rock which repeat upwards and dip slightly, for canyon walls.

There are two ways to use them:

- In the node graph, with the **Paint material mix** node. They are set up as custom terrain types when the graph is applied.
- By hand: in the **Custom Terrain** panel, open the menu of the add button (or of a custom terrain) and choose **Add a material mix**. The mix becomes a custom terrain type you can paint with.

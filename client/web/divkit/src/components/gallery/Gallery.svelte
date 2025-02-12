<script lang="ts">
    import { getContext, onDestroy, onMount } from 'svelte';
    import { derived, Readable } from 'svelte/store';

    import css from './Gallery.module.css';
    import rootCss from '../Root.module.css';
    import arrowsCss from '../utilities/Arrows.module.css';

    import type { Align, LayoutParams } from '../../types/layoutParams';
    import type { DivGalleryData } from '../../types/gallery';
    import type { DivBaseData } from '../../types/base';
    import type { Overflow, SwitchElements } from '../../types/switch-elements';
    import type { Orientation } from '../../types/orientation';
    import type { MaybeMissing } from '../../expressions/json';
    import type { Size } from '../../types/sizes';
    import type { Style } from '../../types/general';
    import type { ComponentContext } from '../../types/componentContext';
    import { ROOT_CTX, RootCtxValue } from '../../context/root';
    import Outer from '../utilities/Outer.svelte';
    import Unknown from '../utilities/Unknown.svelte';
    import { genClassName } from '../../utils/genClassName';
    import { pxToEm } from '../../utils/pxToEm';
    import { makeStyle } from '../../utils/makeStyle';
    import { correctGeneralOrientation } from '../../utils/correctGeneralOrientation';
    import { correctAlignment } from '../../utils/correctAlignment';
    import { assignIfDifferent } from '../../utils/assignIfDifferent';
    import { correctNonNegativeNumber } from '../../utils/correctNonNegativeNumber';
    import { correctEdgeInserts } from '../../utils/correctEdgeInserts';
    import { correctPositiveNumber } from '../../utils/correctPositiveNumber';
    import { joinTemplateSizes } from '../../utils/joinTemplateSizes';
    import { debounce } from '../../utils/debounce';
    import { Truthy } from '../../utils/truthy';

    export let componentContext: ComponentContext<DivGalleryData>;
    export let layoutParams: LayoutParams | undefined = undefined;

    const rootCtx = getContext<RootCtxValue>(ROOT_CTX);

    const direction = rootCtx.direction;

    let scroller: HTMLElement;
    let galleryItemsWrappers: HTMLElement[] = [];
    let hasScrollLeft = false;
    let hasScrollRight = false;

    let resizeObserver: ResizeObserver | null = null;
    let itemsGridElem: HTMLElement;
    let mounted = false;

    const leftClass = rootCtx.getCustomization('galleryLeftClass');
    const rightClass = rootCtx.getCustomization('galleryRightClass');

    let prevId: string | undefined;
    let columns = 1;
    let orientation: Orientation = 'horizontal';
    let align: Align = 'start';
    let gridGap: string | undefined;
    let itemSpacing = 8;
    let crossGridGap: string | undefined;
    let crossSpacing;
    let padding = '';
    let templateSizes: string[] = [];
    let childStore: Readable<(MaybeMissing<Size> | undefined)[]>;
    let scrollerStyle: Style = {};
    let scrollSnap = false;
    let childLayoutParams: LayoutParams = {};
    let defaultItem = 0;

    $: if (componentContext.json) {
        columns = 1;
        orientation = 'horizontal';
        align = 'start';
        itemSpacing = 8;
        padding = '';
    }

    $: jsonItems = Array.isArray(componentContext.json.items) && componentContext.json.items || [];

    $: jsonColumnCount = componentContext.getDerivedFromVars(componentContext.json.column_count);
    $: jsonOrientation = componentContext.getDerivedFromVars(componentContext.json.orientation);
    $: jsonCrossContentAlignment = componentContext.getDerivedFromVars(componentContext.json.cross_content_alignment);
    $: jsonItemSpacing = componentContext.getDerivedFromVars(componentContext.json.item_spacing);
    $: jsonCrossSpacing = componentContext.getDerivedFromVars(componentContext.json.cross_spacing);
    $: jsonPaddings = componentContext.getDerivedFromVars(componentContext.json.paddings);
    $: jsonScrollMode = componentContext.getDerivedFromVars(componentContext.json.scroll_mode);
    $: jsonRestrictParentScroll = componentContext.getDerivedFromVars(componentContext.json.restrict_parent_scroll);
    $: jsonScrollbar = componentContext.getDerivedFromVars(componentContext.json.scrollbar);
    $: jsonDefaultItem = componentContext.getDerivedFromVars(componentContext.json.default_item);

    function replaceItems(items: (MaybeMissing<DivBaseData> | undefined)[]): void {
        componentContext = {
            ...componentContext,
            json: {
                ...componentContext.json,
                items: items.filter(Truthy)
            }
        };
    }

    const isDesktop = rootCtx.isDesktop;

    $: items = jsonItems.map((item, index) => {
        return componentContext.produceChildContext(item, {
            path: index
        });
    });

    $: shouldCheckArrows = $isDesktop && mounted;
    $: if (shouldCheckArrows) {
        if (typeof ResizeObserver !== 'undefined') {
            // Gallery can contain a dynamic content (e.g. loading images with auto-size)
            resizeObserver = new ResizeObserver(() => {
                updateArrowsVisibilityDebounced();
            });
            resizeObserver.observe(itemsGridElem);
        }
    } else if (resizeObserver) {
        resizeObserver.disconnect();
        resizeObserver = null;
    }

    $: {
        columns = correctPositiveNumber($jsonColumnCount, columns);
    }

    function rebuildItemsGrid(items: ComponentContext[], columns: number): ComponentContext[][] {
        let column = 0;
        let res: ComponentContext[][] = [];

        for (let i = 0; i < items.length; ++i) {
            if (!res[column]) {
                res[column] = [];
            }
            res[column].push(items[i]);
            if (++column >= columns) {
                column = 0;
            }
        }

        return res;
    }
    $: itemsGrid = rebuildItemsGrid(items, columns);

    $: {
        orientation = correctGeneralOrientation($jsonOrientation, orientation);
    }

    $: {
        align = correctAlignment($jsonCrossContentAlignment, align);
    }

    $: {
        itemSpacing = correctNonNegativeNumber($jsonItemSpacing, itemSpacing);
        gridGap = pxToEm(itemSpacing);
    }

    $: {
        crossSpacing = correctNonNegativeNumber($jsonCrossSpacing, itemSpacing);
        crossGridGap = pxToEm(crossSpacing);
    }

    $: {
        padding = correctEdgeInserts($jsonPaddings, $direction, padding);
    }

    $: gridTemplate = orientation === 'horizontal' ? 'grid-template-columns' : 'grid-template-rows';
    $: {
        let children: Readable<MaybeMissing<Size> | undefined>[] = [];

        items.forEach(item => {
            const itemSize = orientation === 'horizontal' ? 'width' : 'height';
            children.push(componentContext.getDerivedFromVars(item.json[itemSize]));
        });

        childStore = derived(children, val => val);
    }
    $: {
        templateSizes = [];
        if (columns > 1) {
            // TODO: think about match_parent in this task DIVKIT-307
            templateSizes.push('auto');
        } else {
            $childStore.forEach(childInfo => {
                if ((!childInfo && orientation === 'horizontal') || childInfo?.type === 'match_parent') {
                    templateSizes.push('100%');
                } else {
                    templateSizes.push('max-content');
                }
            });
        }
    }

    $: {
        const newScrollerStyle: Style = {};
        let newChildLayoutParams: LayoutParams = {};
        scrollSnap = false;

        if (orientation === 'horizontal') {
            newChildLayoutParams.parentVAlign = align;
        } else {
            newChildLayoutParams.parentHAlign = align;
        }

        if ($jsonScrollMode === 'paging') {
            scrollSnap = true;
            newChildLayoutParams.scrollSnap = 'start';
            const scrollPadding = orientation === 'horizontal' ? 'scroll-padding-left' : 'scroll-padding-top';
            newScrollerStyle[scrollPadding] = pxToEm(itemSpacing / 2);
        }

        // todo multiple columns
        if (columns === 1) {
            newChildLayoutParams.parentLayoutOrientation = orientation;
        }

        scrollerStyle = assignIfDifferent(newScrollerStyle, scrollerStyle);
        childLayoutParams = assignIfDifferent(newChildLayoutParams, childLayoutParams);
    }

    $: gridStyle = {
        padding,
        'grid-gap': crossGridGap
    };

    $: columnStyle = {
        'grid-gap': gridGap,
        [gridTemplate]: joinTemplateSizes(templateSizes)
    };

    $: mods = {
        orientation,
        'scroll-snap': scrollSnap,
        scrollbar: $jsonScrollbar === 'auto' ? 'auto' : 'none'
    };

    $: {
        defaultItem = correctNonNegativeNumber($jsonDefaultItem, defaultItem);
    }

    function updateArrowsVisibility(): void {
        if (!scroller) {
            return;
        }

        let scrollLeft = scroller.scrollLeft;
        if ($direction === 'rtl') {
            scrollLeft *= -1;
        }
        const scrollWidth = scroller.scrollWidth;
        const containerWidth = scroller.offsetWidth;

        if ($direction === 'ltr') {
            hasScrollLeft = scrollLeft > 2;
            hasScrollRight = scrollLeft + containerWidth < scrollWidth - 2;
        } else {
            hasScrollRight = scrollLeft > 2;
            hasScrollLeft = scrollLeft + containerWidth < scrollWidth - 2;
        }
    }

    const updateArrowsVisibilityDebounced = debounce(updateArrowsVisibility, 50);

    $: if (componentContext.json) {
        updateArrowsVisibilityDebounced();
    }

    function scroll(type: 'left' | 'right'): void {
        scroller.scroll({
            left: scroller.scrollLeft + (scroller.offsetWidth * .75) * (type === 'right' ? 1 : -1),
            behavior: 'smooth'
        });
    }

    function getItems(): HTMLElement[] {
        let res: HTMLElement[] = [];
        let maxLen = galleryItemsWrappers[0].children.length;

        for (let j = 0; j < maxLen; ++j) {
            for (let i = 0; i < columns; ++i) {
                const elem = galleryItemsWrappers[i].children[j] as HTMLElement;
                if (elem) {
                    res.push(elem);
                }
            }
        }

        return res;
    }

    function scrollToGalleryItem(galleryElements: HTMLElement[], index: number, behavior: ScrollBehavior = 'smooth'): void {
        const isHorizontal = orientation === 'horizontal';
        const elementOffset: keyof HTMLElement = isHorizontal ? 'offsetLeft' : 'offsetTop';
        const scrollDirection: keyof ScrollToOptions = isHorizontal ? 'left' : 'top';

        // 0.01 forces Chromium to use scroll-snap (exact correct scroll position will not trigger it)
        // Chromium will save scroll-snapped value and will not save exact one
        // Saved scroll position is used on resnapping (e.g. content change)

        const elem = galleryElements[index];

        if (elem) {
            scroller.scroll({
                [scrollDirection]: Math.max(0, elem[elementOffset] + .01 - itemSpacing / 2),
                behavior
            });
        }
    }

    function checkIsIntersecting(scroller: DOMRect, item: DOMRect): boolean {
        if (orientation === 'horizontal') {
            return item.right > scroller.left && scroller.right > item.left;
        }

        return item.bottom > scroller.top && scroller.bottom > item.top;
    }

    function checkIsFullyIntersecting(scroller: DOMRect, item: DOMRect): boolean {
        if (orientation === 'horizontal') {
            return item.left >= scroller.left && item.right <= scroller.right;
        }

        return item.top >= scroller.top && item.bottom <= scroller.bottom;
    }

    function calculateCurrentElementIndex(action: 'prev' | 'next'): number {
        const galleryElements = getItems();
        const scrollerRect = scroller.getBoundingClientRect();

        // Try to find the most left fully visible element
        const firstFullyVisibleElement = galleryElements.findIndex(el =>
            checkIsFullyIntersecting(scrollerRect, el.getBoundingClientRect())
        );

        if (firstFullyVisibleElement !== -1) {
            return firstFullyVisibleElement;
        }

        // If there is no fully visible elements, it means that:
        // - only one element is partly visible and its width bigger than gallery width
        // - two elements are partly visible
        const visibleElementsMap = galleryElements.map(el =>
            checkIsIntersecting(scrollerRect, el.getBoundingClientRect())
        );
        const firstVisibleElement = visibleElementsMap.findIndex(Boolean);

        if (firstVisibleElement !== -1) {
            // If two elements are partly visible, we should scroll to current element on "set_item_previous" action
            const isPreviousEqualCurrent = action === 'prev' && visibleElementsMap.filter(Boolean).length === 2;
            return isPreviousEqualCurrent ? firstVisibleElement + 1 : firstVisibleElement;
        }

        return action === 'prev' ? 1 : galleryElements.length - 2;
    }

    $: if (componentContext.json) {
        if (prevId) {
            rootCtx.unregisterInstance(prevId);
            prevId = undefined;
        }

        if (componentContext.json.id && !componentContext.fakeElement) {
            prevId = componentContext.json.id;
            rootCtx.registerInstance<SwitchElements>(prevId, {
                setCurrentItem(item: number) {
                    const galleryElements = getItems();
                    if (item < 0 || item > galleryElements.length - 1) {
                        throw new Error('Item is out of range in "set-current-item" action');
                    }

                    scrollToGalleryItem(galleryElements, item);
                },
                setPreviousItem(overflow: Overflow) {
                    const currentElementIndex = calculateCurrentElementIndex('prev');
                    const galleryElements = getItems();
                    let previousItem = currentElementIndex - 1;

                    if (previousItem < 0) {
                        previousItem = overflow === 'ring' ? galleryElements.length - 1 : currentElementIndex;
                    }

                    scrollToGalleryItem(galleryElements, previousItem);
                },
                setNextItem(overflow: Overflow) {
                    // Go to scroller start, if we reached right/bottom edge of scroller
                    const isEdgeScroll = orientation === 'horizontal' ? (
                        scroller.scrollLeft + scroller.offsetWidth === scroller.scrollWidth
                    ) : (
                        scroller.scrollTop + scroller.offsetHeight === scroller.scrollHeight
                    );
                    const galleryElements = getItems();
                    if (isEdgeScroll && overflow === 'ring') {
                        scrollToGalleryItem(galleryElements, 0);
                        return;
                    }

                    const currentElementIndex = calculateCurrentElementIndex('next');
                    let nextItem = currentElementIndex + 1;

                    if (nextItem > galleryElements.length - 1) {
                        nextItem = overflow === 'ring' ? 0 : currentElementIndex;
                    }

                    scrollToGalleryItem(galleryElements, nextItem);
                }
            });
        }
    }

    onMount(() => {
        mounted = true;

        updateArrowsVisibility();

        if (defaultItem) {
            const galleryElements = getItems();
            scrollToGalleryItem(galleryElements, defaultItem, 'auto');
        }
    });

    onDestroy(() => {
        mounted = false;

        if (prevId && !componentContext.fakeElement) {
            rootCtx.unregisterInstance(prevId);
            prevId = undefined;
        }
    });
</script>

<svelte:window on:resize={shouldCheckArrows ? updateArrowsVisibilityDebounced : null} />

<Outer
    cls={genClassName('gallery', css, mods)}
    {componentContext}
    {layoutParams}
    customPaddings={true}
    customActions={'gallery'}
    parentOf={jsonItems}
    {replaceItems}
>
    <div
        class="{css.gallery__scroller} {$jsonRestrictParentScroll ? rootCss['root_restrict-scroll'] : ''}"
        bind:this={scroller}
        on:scroll={shouldCheckArrows ? updateArrowsVisibility : null}
        style={makeStyle(scrollerStyle)}
    >
        <div
            bind:this={itemsGridElem}
            class={css['gallery__items-grid']}
            style={makeStyle(gridStyle)}
        >
            {#each itemsGrid as itemsRow, rowIndex}
                <div
                    class={css.gallery__items}
                    style={makeStyle(columnStyle)}
                    bind:this={galleryItemsWrappers[rowIndex]}
                >
                    {#each itemsRow as item}
                        <Unknown
                            componentContext={item}
                            layoutParams={childLayoutParams}
                        />
                    {/each}
                </div>
            {/each}
        </div>
    </div>
    {#if orientation === 'horizontal'}
        {#if hasScrollLeft && shouldCheckArrows}
            <!-- svelte-ignore a11y-click-events-have-key-events -->
            <!-- svelte-ignore a11y-no-static-element-interactions -->
            <div class="{leftClass || `${css.gallery__arrow} ${arrowsCss.arrow} ${arrowsCss.arrow_left}`}" on:click={() => scroll('left')}>
                {#if !leftClass}
                    <svg class={arrowsCss.arrow__icon} xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" fill="none">
                        <path class={css['gallery__arrow-icon-path']} d="m10 16 8.3 8 1.03-1-4-6-.7-1 .7-1 4-6-1.03-1z"/>
                    </svg>
                {/if}
            </div>
        {/if}
        {#if hasScrollRight && shouldCheckArrows}
            <!-- svelte-ignore a11y-click-events-have-key-events -->
            <!-- svelte-ignore a11y-no-static-element-interactions -->
            <div class="{rightClass || `${css.gallery__arrow} ${arrowsCss.arrow} ${arrowsCss.arrow_right}`}" on:click={() => scroll('right')}>
                {#if !rightClass}
                    <svg class={arrowsCss.arrow__icon} xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" fill="none">
                        <path class={css['gallery__arrow-icon-path']} d="M22 16l-8.3 8-1.03-1 4-6 .7-1-.7-1-4-6 1.03-1 8.3 8z"/>
                    </svg>
                {/if}
            </div>
        {/if}
    {/if}
</Outer>

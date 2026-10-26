package com.spidertracker.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SearchCyan=Color(0xff00d7fa)

@Composable fun SearchShelf(vm:TrackerViewModel,m:TrackerLayout,onSearch:()->Unit,onEdit:(String)->Unit={vm.editSearch(it)},onFocus:()->Unit={},suggestionRight:Float=m.width,componentRight:Float=m.width) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val submit={keyboard?.hide();focus.clearFocus();onSearch()}
    var selected by remember(vm.searchResults) {mutableIntStateOf(-1)}
    val choose:(AddressResult)->Unit={keyboard?.hide();focus.clearFocus();if(vm.placeState==PlaceUiState.Navigating)vm.notify("STOP NAVIGATION TO CHANGE DESTINATION") else vm.chooseDestination(it)}
    val normalWidth=(m.width-96).coerceIn(160f,600f).coerceAtMost(m.width-16)
    val x=(m.width-normalWidth)/2
    val width=normalWidth.coerceAtMost((componentRight-x).coerceAtLeast(120f))
    val inputStyle=TextStyle(color=SearchCyan,fontFamily=PixelFont,fontSize=14.sp,lineHeight=17.5.sp,
        platformStyle=PlatformTextStyle(includeFontPadding=false))
    Box(Modifier.offset(x.dp,m.shelfY.dp).size(width.dp,48.dp).testTag("search-component")) {
        Box(Modifier.fillMaxWidth().padding(end=48.dp).fillMaxHeight().testTag("search-frame"),contentAlignment=Alignment.Center) {
            PixelSearchBackground(Modifier.fillMaxWidth().height(40.dp).testTag("search-art"))
            Row(Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Canvas(Modifier.size(20.dp).testTag("search-icon")) {
                    pixelCircle(SearchCyan,6.dp.toPx(),Offset(7.dp.toPx(),7.dp.toPx()),width=1.5.dp.toPx())
                    pixelLine(SearchCyan,Offset(12.dp.toPx(),12.dp.toPx()),Offset(19.dp.toPx(),19.dp.toPx()),1.5.dp.toPx())
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(vm.searchQuery,onEdit,Modifier.weight(1f).height(40.dp).wrapContentHeight(Alignment.CenterVertically)
                    .onPreviewKeyEvent {event->
                        if(event.type!=KeyEventType.KeyDown || vm.searchResults.isEmpty())false
                        else when(event.key) {
                            Key.DirectionDown->{selected=(selected+1).coerceAtMost(vm.searchResults.lastIndex);true}
                            Key.DirectionUp->{selected=(selected-1).coerceAtLeast(0);true}
                            Key.Enter,Key.NumPadEnter->if(selected>=0){choose(vm.searchResults[selected]);true}else false
                            else->false
                        }
                    }.onFocusChanged {if(it.isFocused){onFocus();vm.reopenSearch()}}.testTag("address-input").semantics {
                        contentDescription="Search address"
                        customActions=listOf(CustomAccessibilityAction("Clear input"){onEdit("");true})
                    },
                    textStyle=inputStyle,singleLine=true,cursorBrush=SolidColor(SearchCyan),
                    keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Words,imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={submit()}),decorationBox={inner->
                        Box(contentAlignment=Alignment.CenterStart) {
                            if(vm.searchQuery.isEmpty())BasicText("SEARCH ADDRESS...",style=inputStyle,maxLines=1,overflow=TextOverflow.Ellipsis)
                            inner()
                        }
                    })
                if(vm.searchQuery.isNotEmpty() && width>=184f)Box(Modifier.size(48.dp).clickable(role=Role.Button){onEdit("")}
                    .semantics {contentDescription="Clear input"}.testTag("search-clear"),contentAlignment=Alignment.Center) {PixelText("×",size=16,color=SearchCyan)}
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).size(54.dp,48.dp).clickable(role=Role.Button,onClick=submit)
            .semantics {contentDescription="Search"}.testTag("search-submit"),contentAlignment=Alignment.Center) {
            SuppliedImage(R.drawable.search_button,null,Modifier.size(54.dp,40.dp).testTag("search-button-art"))
        }
    }
    if(vm.searchVisible) {
        val headerBottom=maxOf(53f,16f+(m.width*.49f).coerceAtMost(280f)*47/280)
        val available=(m.shelfY-headerBottom-8).coerceAtLeast(56f)
        val compact=available<160f
        val panelWidth=width.coerceAtMost((suggestionRight-x-8).coerceAtLeast(96f))
        val panelHeight=(if(vm.searchResults.isEmpty())96f else 48f+vm.searchResults.size*48f).coerceAtMost(available).coerceAtMost(304f)
        Column(Modifier.offset(x.dp,(m.shelfY-panelHeight-4).dp).size(panelWidth.dp,panelHeight.dp)
            .background(Color(0xff0e1c3b)).border(1.dp,Ink.Cyan).testTag("search-results")) {
            if(!compact || vm.searchResults.isEmpty())Row(Modifier.fillMaxWidth().heightIn(min=32.dp),verticalAlignment=Alignment.CenterVertically) {
                PixelText(vm.searchMessage,Modifier.weight(1f).padding(8.dp).semantics {liveRegion=LiveRegionMode.Polite},9,maxLines=2)
                Box(Modifier.size(48.dp).clickable(role=Role.Button){vm.hideSearch()}.semantics {contentDescription="Dismiss search results"}.testTag("search-dismiss"),contentAlignment=Alignment.Center) {PixelText("×",size=16)}
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                vm.searchResults.forEachIndexed {index,result->
                    Column(Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .background(if(selected==index)Color(0xff18415a) else Color.Transparent)
                        .clickable(role=Role.Button){choose(result)}.semantics {this.selected=selected==index}
                        .padding(horizontal=8.dp,vertical=6.dp).testTag("search-result-$index")) {
                        PixelText(result.name ?: result.label,size=11,color=Ink.Cream,maxLines=1)
                        val context=listOfNotNull(result.locality,result.country).distinct().joinToString(" · ")
                        if(context.isNotBlank())PixelText(context,Modifier.padding(top=4.dp),9,Ink.Cyan,maxLines=1)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Cyan.copy(alpha=.35f)))
                }
            }
            if(!compact)PixelText("Photon · © OpenStreetMap contributors",Modifier.padding(4.dp),7,Ink.Pale,maxLines=1)
        }
    }
}

/** Overlay-only IME layout. The native map keeps its independent full-screen constraints. */
@Composable internal fun BottomSearchOverlay(vm:TrackerViewModel,m:TrackerLayout,clock:ForegroundAnimationClock,
    capture:Boolean,reducedMotion:Boolean,onSearch:()->Unit,onEdit:(String)->Unit,onFocus:()->Unit,searchRight:Float=m.width) {
    Box(Modifier.fillMaxSize().testTag("bottom-search-overlay")) {
        SearchShelf(vm,m,onSearch,onEdit,onFocus,suggestionRight=m.radarX,componentRight=searchRight)
        AnimatedCharacter(clock,capture,reducedMotion,Modifier.offset(10.dp,(m.height-61).dp).size(32.dp,(32f*43/27).dp))
    }
}

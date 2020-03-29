/*
 * Copyright (c) 2020, Wild Adventure
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 4. Redistribution of this software in source or binary forms shall be free
 *    of all charges or fees to the recipient of this software.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package wild.api;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import lombok.NonNull;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Puoi importare tutti i metodi in modo statico: import wild.api.BungeeCommons.*;
 */
public class BungeeCommons {
	
	private static final Pattern URL_PATTERN = Pattern.compile("(https?://)?([\\w\\.\\-]{2,}\\.[a-z]{2,4})(/[^\\s" + Pattern.quote("\"()[],;") + "]*)?");
	
	public static void pauseThread(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// Ignora
		}
	}
	
	public static BaseComponent[] fixLinks(BaseComponent[] components) {
		return fixLinks(components, new LinkGenerator() {
			
			@Override
			public TextComponent generate(String text, String fullUrl) {
				TextComponent component = new TextComponent(text);
				component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, fullUrl));
				component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						new ComponentBuilder("Clicca per aprire il link: ").color(ChatColor.GRAY)
						.append(text).color(ChatColor.AQUA).create()));
				return component;
			}
		});
	}
	
	public static BaseComponent[] fixLinks(@NonNull BaseComponent[] components, @NonNull LinkGenerator generator) {
		for (BaseComponent component : components) {
			if (component instanceof TextComponent) {
				TextComponent textComponent = (TextComponent) component;
				String text = textComponent.getText();

				Matcher matcher = URL_PATTERN.matcher(text);
				if (matcher.find()) {
					List<BaseComponent> parts = Lists.newArrayList();
					int pos = 0;
					boolean find = true;
					
					while (find) {
						if (pos != matcher.start()) { // Se ci sono due match di seguito la parte in mezzo rimane vuota
							parts.add(new TextComponent(text.substring(pos, matcher.start())));
						}
						
						String url;
						if (matcher.group(1) == null) { // Aggiunge il protocollo se non c'è
							url = "http://" + matcher.group();
						} else {
							url = matcher.group();
						}
						
						TextComponent linkComponent = generator.generate(matcher.group(), url);
						if (linkComponent == null) {
							throw new IllegalStateException("LinkGenerator.generate() returned null");
						}
						parts.add(linkComponent);

					    pos = matcher.end();
					    find = matcher.find();
					}
					
					if (pos != text.length() - 1) { // Se si è arrivati fino alla fine della stringa non ci sono rimanenze
						parts.add(new TextComponent(text.substring(pos)));
					}
					
					textComponent.setText("");
					textComponent.setExtra(parts);
				}
			}
		}
		
		return components;
	}
	
	public static String color(String input) {
		if (input == null) return null;
		
		return ChatColor.translateAlternateColorCodes('&', input);
	}

}

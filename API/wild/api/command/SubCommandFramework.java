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
package wild.api.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;
import wild.api.chat.Chat;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class SubCommandFramework extends CommandFramework {
	
	private Map<String, SubCommandPiece> subCommands;
	@Setter @NonNull private ChatColor helpColor = ChatColor.GRAY;
	@Setter @NonNull private BaseComponent[] helpHeader = Chat.makeArray("/" + getName() + ":", ChatColor.WHITE);

	public SubCommandFramework(Plugin plugin, String label, String... aliases) {
		super(plugin, label, aliases);
		subCommands = Maps.newLinkedHashMap();
		
		for (Method method : getClass().getDeclaredMethods()) {
			if (method.getAnnotation(SubCommand.class) != null) {

				String subCommandName = method.getAnnotation(SubCommand.class).value();
				String subCommandPermission = method.getAnnotation(SubCommandPermission.class) != null ? method.getAnnotation(SubCommandPermission.class).value() : null;
				String subCommandNoPermissionMessage = method.getAnnotation(SubCommandNoPermissionMessage.class) != null ? method.getAnnotation(SubCommandNoPermissionMessage.class).value() : null;
				String subCommandUsage = method.getAnnotation(SubCommandUsage.class) != null ? method.getAnnotation(SubCommandUsage.class).value() : null;
				int minArgs = method.getAnnotation(SubCommandMinArgs.class) != null ? method.getAnnotation(SubCommandMinArgs.class).value() : 0;
				boolean subCommandAsyncSQL = method.getAnnotation(SubCommandAsyncSQL.class) != null;
				String subCommandAsyncSQLMessage = method.getAnnotation(SubCommandAsyncSQL.class) != null ? method.getAnnotation(SubCommandAsyncSQL.class).value() : null;
				
				try {
					method.setAccessible(true);
					
					Class<?>[] params = method.getParameterTypes();
					if (params == null || params.length != 2 || params[0] != CommandSender.class || params[1] != String[].class) {
						throw new IllegalArgumentException("I parametri del sottocomando devono essere 2, in ordine: CommandSender (sender), String[] (args)");
					}
					
					SubCommandPiece piece = new SubCommandPiece(method, subCommandPermission, subCommandNoPermissionMessage, subCommandUsage, minArgs, subCommandAsyncSQL, subCommandAsyncSQLMessage);
					subCommands.put(subCommandName.toLowerCase(), piece);
					
				} catch (Exception e) {
					ProxyServer.getInstance().getLogger().log(Level.SEVERE, "Impossibile aggiungere il sottocomando " + subCommandName, e);
				}
			}
		}
	}
	
	@Override
	public final void onCommand(final CommandSender sender, String[] args) {
		if (args.length == 0) {
			sendHelpCommands(sender);
			return;
		}
		
		String subCommandName = args[0].toLowerCase();
		final SubCommandPiece piece = subCommands.get(subCommandName);
		if (piece != null) {
				
			if (piece.permission != null && !sender.hasPermission(piece.permission)) {
				sender.sendMessage(piece.noPermissionMessage);
				return;
			}
			
			final String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
			if (subArgs.length < piece.minArgs) {
				Chat.tell(sender, "Utilizzo comando: " + "/" + getName() + " " + subCommandName + (piece.usage != null ? " " + piece.usage : ""), ChatColor.RED);
				return;
			}
			
			if (piece.asyncSQL) {
				ProxyServer.getInstance().getScheduler().runAsync(getPlugin(), new Runnable() {
					
					@Override
					public void run() {
						tryInvoke(piece, sender, subArgs);
					}
				});
				
			} else {
				tryInvoke(piece, sender, subArgs);
			}

		} else {
			tellUnknownSubCommand(sender);
		}
	}
	
	@SneakyThrows
	private void tryInvoke(SubCommandPiece piece, CommandSender sender, String[] subArgs) {
		try {
			piece.method.invoke(this, sender, subArgs);
		
		} catch (Throwable t) {
			
			Throwable cause = t instanceof InvocationTargetException ? ((InvocationTargetException) t).getCause() : t;

			if (cause instanceof ExecuteException) {
				// Totally fine
				if (cause.getMessage() != null) {
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + cause.getMessage()));
				}
				
			} else {
				if (piece.asyncSQL && cause instanceof SQLException) {
					if (piece.asyncSQLMessage != null) {
						sender.sendMessage(piece.asyncSQLMessage);
					}
				} else {
					Chat.tell(sender, "Si è verificato un errore interno durante l'esecuzione del comando.", ChatColor.RED);
				}
				
				getPlugin().getLogger().log(Level.SEVERE, "Error while " + sender.getName() + " was executing /" + getName() + ":", cause);
			}
		}
	}
	
	public final Collection<SubCommandPiece> getSubcommands() {
		return subCommands.values();
	}
	
	public void sendHelpCommands(CommandSender sender) {
		List<TextComponent> accessibleCommands = Lists.newArrayList();
		
		for (Entry<String, SubCommandPiece> entry : subCommands.entrySet()) {
			String command = entry.getKey();
			SubCommandPiece piece = entry.getValue();
			if (piece.permission == null || sender.hasPermission(piece.permission)) {
				accessibleCommands.add(Chat.make("/" + getName() + " " + command + (piece.usage != null ? " " + piece.usage : ""), helpColor));
			}
		}
		
		if (accessibleCommands.isEmpty()) {
			sender.sendMessage(super.noPermissionMessage);

		} else {
			sender.sendMessage(helpHeader);
			for (TextComponent accessibleCommand : accessibleCommands) {
				sender.sendMessage(accessibleCommand);
			}
		}
	}
	
	public void tellUnknownSubCommand(CommandSender sender) {
		Chat.tell(sender, "Sotto-comando sconosciuto. Scrivi /" + getName() + " per ottenere aiuto.", ChatColor.RED);
	}
	
	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface SubCommand {

		String value();
		
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface SubCommandPermission {

		String value();
		
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface SubCommandNoPermissionMessage {

		String value();

	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface SubCommandUsage {

		String value();

	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface SubCommandMinArgs {

		int value();

	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface SubCommandAsyncSQL {

		String value();
		
	}
	

	private static class SubCommandPiece {
		
		private Method method;
		private String permission;
		private BaseComponent[] noPermissionMessage;
		private String usage;
		private int minArgs;
		private boolean asyncSQL;
		private BaseComponent[] asyncSQLMessage;
		
		
		public SubCommandPiece(Method method, String permission, String noPermissionMessage, String usage, int minArgs, boolean asyncSQL, String asyncSQLMessage) {
			this.method = method;
			this.permission = permission;
			if (noPermissionMessage != null) {
				this.noPermissionMessage = TextComponent.fromLegacyText(ChatColor.RED + noPermissionMessage);
			} else {
				this.noPermissionMessage = Chat.makeArray("Non hai il permesso per usare questo sotto-comando.", ChatColor.RED);
			}
			this.usage = usage;
			this.minArgs = minArgs;
			this.asyncSQL = asyncSQL;
			if (asyncSQLMessage != null) {
				this.asyncSQLMessage = TextComponent.fromLegacyText(ChatColor.RED + asyncSQLMessage);
			}
		}
		
		
	}
	
	
}
